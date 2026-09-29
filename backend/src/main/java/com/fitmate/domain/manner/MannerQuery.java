package com.fitmate.domain.manner;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MannerQuery {

    /** 모임이 끝난 뒤 14일, 1:1 매칭은 수락 뒤 30일 안에만 평가할 수 있다 */
    private static final String GATHERING_WINDOW = "interval '14 days'";
    private static final String MATCH_WINDOW = "interval '30 days'";

    private static final String PENDING_SQL = """
            SELECT * FROM (
                SELECT u.id AS target_id, u.nickname, u.profile_image_url,
                       g.id AS gathering_id, NULL::BIGINT AS match_request_id,
                       g.title AS context, g.starts_at AS happened_at
                FROM gatherings g
                JOIN gathering_participants me ON me.gathering_id = g.id AND me.user_id = :me AND me.status = 'JOINED'
                JOIN gathering_participants t ON t.gathering_id = g.id AND t.user_id <> :me AND t.status = 'JOINED'
                JOIN users u ON u.id = t.user_id
                WHERE g.status <> 'CANCELED'
                  AND g.starts_at <= now() AND g.starts_at > now() - %1$s
                  AND NOT EXISTS (SELECT 1 FROM manner_reviews r
                                  WHERE r.reviewer_id = :me AND r.target_id = u.id AND r.gathering_id = g.id)
                UNION ALL
                SELECT u.id, u.nickname, u.profile_image_url,
                       NULL, m.id,
                       s.name, m.responded_at
                FROM match_requests m
                JOIN users u ON u.id = CASE WHEN m.requester_id = :me THEN m.receiver_id ELSE m.requester_id END
                JOIN sports s ON s.id = m.sport_id
                WHERE m.status = 'ACCEPTED' AND (m.requester_id = :me OR m.receiver_id = :me)
                  AND m.responded_at > now() - %2$s
                  AND NOT EXISTS (SELECT 1 FROM manner_reviews r
                                  WHERE r.reviewer_id = :me AND r.target_id = u.id AND r.match_request_id = m.id)
            ) p
            WHERE NOT EXISTS (SELECT 1 FROM user_blocks b
                              WHERE (b.blocker_id = :me AND b.blocked_id = p.target_id)
                                 OR (b.blocker_id = p.target_id AND b.blocked_id = :me))
            ORDER BY p.happened_at DESC
            LIMIT 50
            """.formatted(GATHERING_WINDOW, MATCH_WINDOW);

    private static final String GATHERING_ELIGIBLE_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM gatherings g
                JOIN gathering_participants me ON me.gathering_id = g.id AND me.user_id = :me AND me.status = 'JOINED'
                JOIN gathering_participants t ON t.gathering_id = g.id AND t.user_id = :target AND t.status = 'JOINED'
                WHERE g.id = :id AND g.status <> 'CANCELED'
                  AND g.starts_at <= now() AND g.starts_at > now() - %s)
            """.formatted(GATHERING_WINDOW);

    private static final String MATCH_ELIGIBLE_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM match_requests m
                WHERE m.id = :id AND m.status = 'ACCEPTED' AND m.responded_at > now() - %s
                  AND ((m.requester_id = :me AND m.receiver_id = :target)
                    OR (m.requester_id = :target AND m.receiver_id = :me)))
            """.formatted(MATCH_WINDOW);

    private static final String[] POSITIVE_TAGS = Arrays.stream(MannerReview.Tag.values())
            .filter(MannerReview.Tag::isPositive).map(Enum::name).toArray(String[]::new);

    private final JdbcClient jdbcClient;

    public List<MannerDtos.Pending> findPending(Long userId) {
        return jdbcClient.sql(PENDING_SQL)
                .param("me", userId)
                .query((rs, rowNum) -> new MannerDtos.Pending(
                        rs.getLong("target_id"),
                        rs.getString("nickname"),
                        rs.getString("profile_image_url"),
                        rs.getObject("gathering_id", Long.class),
                        rs.getObject("match_request_id", Long.class),
                        rs.getString("context"),
                        rs.getObject("happened_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public boolean canReviewGathering(Long reviewerId, Long targetId, Long gatheringId) {
        return eligible(GATHERING_ELIGIBLE_SQL, reviewerId, targetId, gatheringId);
    }

    public boolean canReviewMatch(Long reviewerId, Long targetId, Long matchRequestId) {
        return eligible(MATCH_ELIGIBLE_SQL, reviewerId, targetId, matchRequestId);
    }

    private boolean eligible(String sql, Long reviewerId, Long targetId, Long id) {
        return Boolean.TRUE.equals(jdbcClient.sql(sql)
                .param("me", reviewerId).param("target", targetId).param("id", id)
                .query(Boolean.class).single());
    }

    public long countReviews(Long targetId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM manner_reviews WHERE target_id = :id")
                .param("id", targetId).query(Long.class).single();
    }

    public List<MannerDtos.TagCount> positiveTagCounts(Long targetId) {
        return jdbcClient.sql("""
                        SELECT tag, COUNT(*) AS cnt
                        FROM manner_reviews r, unnest(r.tags) AS tag
                        WHERE r.target_id = :id AND tag = ANY(:positive)
                        GROUP BY tag
                        ORDER BY cnt DESC, tag
                        """)
                .param("id", targetId)
                .param("positive", POSITIVE_TAGS)
                .query((rs, rowNum) -> new MannerDtos.TagCount(
                        MannerReview.Tag.valueOf(rs.getString("tag")), rs.getLong("cnt")))
                .list();
    }
}

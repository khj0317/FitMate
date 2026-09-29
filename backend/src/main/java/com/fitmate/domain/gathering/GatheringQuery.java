package com.fitmate.domain.gathering;

import com.fitmate.domain.gathering.dto.GatheringDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 모임 목록은 위치 반경·차단·참여 여부를 함께 봐야 해서 한 번의 네이티브 SQL로 가져온다. */
@Repository
@RequiredArgsConstructor
public class GatheringQuery {

    private static final String SELECT = """
            SELECT g.id, g.title, g.place_name, g.starts_at, g.capacity, g.current_count, g.status,
                   s.id AS sport_id, s.code AS sport_code, s.name AS sport_name,
                   h.id AS host_id, h.nickname AS host_nickname, h.profile_image_url AS host_image,
                   h.manner_score AS host_manner,
                   ST_Distance(g.location, me.activity_location) AS distance_meters,
                   EXISTS (SELECT 1 FROM gathering_participants p
                           WHERE p.gathering_id = g.id AND p.user_id = me.id AND p.status = 'JOINED') AS joined
            FROM gatherings g
            JOIN users me ON me.id = :userId
            JOIN users h ON h.id = g.host_id
            JOIN sports s ON s.id = g.sport_id
            """;

    /** 앞으로 열리는 주변 모임 (취소 제외, 차단 관계인 모임장 제외), 가까운 날짜순 */
    private static final String NEARBY = SELECT + """
            WHERE g.status IN ('RECRUITING', 'CLOSED')
              AND g.starts_at > now()
              AND ST_DWithin(g.location, me.activity_location, :radiusMeters)
              AND (CAST(:sportId AS SMALLINT) IS NULL OR g.sport_id = CAST(:sportId AS SMALLINT))
              AND NOT EXISTS (
                  SELECT 1 FROM user_blocks b
                  WHERE (b.blocker_id = me.id AND b.blocked_id = h.id) OR (b.blocker_id = h.id AND b.blocked_id = me.id)
              )
            ORDER BY g.starts_at
            LIMIT 50
            """;

    /** 내가 참여한 모임 (다가오는 모임이 먼저, 지난 모임은 최근 것부터) */
    private static final String MINE = SELECT + """
            JOIN gathering_participants mp ON mp.gathering_id = g.id AND mp.user_id = me.id AND mp.status = 'JOINED'
            WHERE g.status <> 'CANCELED'
            ORDER BY (g.starts_at < now()), CASE WHEN g.starts_at >= now() THEN g.starts_at END,
                     g.starts_at DESC
            LIMIT 50
            """;

    private static final String ONE = SELECT + " WHERE g.id = :gatheringId";

    private final JdbcClient jdbcClient;

    public List<GatheringDtos.Summary> findNearby(Long userId, double radiusMeters, Short sportId) {
        return jdbcClient.sql(NEARBY)
                .param("userId", userId)
                .param("radiusMeters", radiusMeters)
                .param("sportId", sportId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    public List<GatheringDtos.Summary> findMine(Long userId) {
        return jdbcClient.sql(MINE).param("userId", userId).query((rs, rowNum) -> map(rs)).list();
    }

    public Optional<GatheringDtos.Summary> findOne(Long userId, Long gatheringId) {
        return jdbcClient.sql(ONE).param("userId", userId).param("gatheringId", gatheringId)
                .query((rs, rowNum) -> map(rs)).optional();
    }

    public List<GatheringDtos.Participant> findParticipants(Long gatheringId) {
        return jdbcClient.sql("""
                        SELECT u.id, u.nickname, u.profile_image_url, u.manner_score, (u.id = g.host_id) AS host
                        FROM gathering_participants p
                        JOIN users u ON u.id = p.user_id
                        JOIN gatherings g ON g.id = p.gathering_id
                        WHERE p.gathering_id = :gatheringId AND p.status = 'JOINED'
                        ORDER BY (u.id = g.host_id) DESC, p.joined_at
                        """)
                .param("gatheringId", gatheringId)
                .query((rs, rowNum) -> new GatheringDtos.Participant(rs.getLong("id"), rs.getString("nickname"),
                        rs.getString("profile_image_url"), rs.getBigDecimal("manner_score"), rs.getBoolean("host")))
                .list();
    }

    private static GatheringDtos.Summary map(ResultSet rs) throws SQLException {
        Instant startsAt = rs.getObject("starts_at", OffsetDateTime.class).toInstant();
        Gathering.Status stored = Gathering.Status.valueOf(rs.getString("status"));
        // 시작 시각이 지난 모임은 끝난 모임으로 보여준다
        Gathering.Status status = stored != Gathering.Status.CANCELED && !startsAt.isAfter(Instant.now())
                ? Gathering.Status.COMPLETED : stored;
        double meters = rs.getDouble("distance_meters");
        Double distanceKm = rs.wasNull() ? null : Math.max(0.5, Math.ceil(meters / 500.0) * 0.5);

        return new GatheringDtos.Summary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getShort("sport_id"),
                rs.getString("sport_code"),
                rs.getString("sport_name"),
                rs.getString("place_name"),
                startsAt,
                rs.getShort("capacity"),
                rs.getShort("current_count"),
                status,
                new GatheringDtos.Host(rs.getLong("host_id"), rs.getString("host_nickname"),
                        rs.getString("host_image"), rs.getBigDecimal("host_manner")),
                distanceKm,
                rs.getBoolean("joined"));
    }
}

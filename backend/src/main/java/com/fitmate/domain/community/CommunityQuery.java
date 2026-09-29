package com.fitmate.domain.community;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 피드는 작성자·종목·좋아요 여부·차단 관계를 함께 봐야 해서 네이티브 SQL로 한 번에 가져오고,
 * 사진은 글 ID 목록으로 한 번 더 조회해서 붙인다 (글마다 따로 조회하는 N+1 방지).
 */
@Repository
@RequiredArgsConstructor
public class CommunityQuery {

    private static final String SELECT = """
            SELECT p.id, p.category, p.sport_id, s.code AS sport_code, s.name AS sport_name, p.content, p.area_name,
                   p.like_count, p.comment_count, p.created_at, p.updated_at,
                   a.id AS author_id, a.nickname, a.profile_image_url, a.manner_score,
                   EXISTS (SELECT 1 FROM post_likes l WHERE l.post_id = p.id AND l.user_id = me.id) AS liked
            FROM posts p
            JOIN users a ON a.id = p.author_id
            JOIN users me ON me.id = :me
            LEFT JOIN sports s ON s.id = p.sport_id
            """;

    /** 차단 관계(어느 쪽이든)인 사람의 글은 보이지 않는다 */
    private static final String NOT_BLOCKED = """
            NOT EXISTS (SELECT 1 FROM user_blocks b
                        WHERE (b.blocker_id = me.id AND b.blocked_id = a.id)
                           OR (b.blocker_id = a.id AND b.blocked_id = me.id))
            """;

    private static final String FEED = SELECT + """
            WHERE NOT p.hidden
              AND (CAST(:cursor AS BIGINT) IS NULL OR p.id < CAST(:cursor AS BIGINT))
              AND (CAST(:category AS VARCHAR) IS NULL OR p.category = CAST(:category AS VARCHAR))
              AND (CAST(:sportId AS SMALLINT) IS NULL OR p.sport_id = CAST(:sportId AS SMALLINT))
              AND (CAST(:authorId AS BIGINT) IS NULL OR p.author_id = CAST(:authorId AS BIGINT))
              AND (NOT :nearby OR ST_DWithin(p.location, me.activity_location, :radiusMeters))
              AND """ + " " + NOT_BLOCKED + """
            ORDER BY p.id DESC
            LIMIT :limit
            """;

    private static final String ONE = SELECT + " WHERE p.id = :postId AND NOT p.hidden AND " + NOT_BLOCKED;

    private final JdbcClient jdbcClient;

    public List<CommunityDtos.PostItem> findFeed(Long me, Post.Category category, Short sportId, Long authorId,
                                                 boolean nearby, double radiusMeters, Long cursor, int limit) {
        List<CommunityDtos.PostItem> posts = jdbcClient.sql(FEED)
                .param("me", me)
                .param("cursor", cursor)
                .param("category", category == null ? null : category.name())
                .param("sportId", sportId)
                .param("authorId", authorId)
                .param("nearby", nearby)
                .param("radiusMeters", radiusMeters)
                .param("limit", limit)
                .query((rs, rowNum) -> mapPost(rs, me))
                .list();
        return attachImages(posts);
    }

    public Optional<CommunityDtos.PostItem> findOne(Long me, Long postId) {
        return jdbcClient.sql(ONE)
                .param("me", me)
                .param("postId", postId)
                .query((rs, rowNum) -> mapPost(rs, me))
                .optional()
                .map(post -> attachImages(List.of(post)).get(0));
    }

    /** 오래된 순. 차단 관계인 사람의 댓글은 빼고, 탈퇴한 회원의 댓글은 작성자 없이 남긴다 */
    public List<CommunityDtos.CommentItem> findComments(Long me, Long postId) {
        return jdbcClient.sql("""
                        SELECT c.id, c.parent_id, c.content, c.deleted, c.created_at,
                               u.id AS author_id, u.nickname, u.profile_image_url, u.manner_score
                        FROM comments c
                        LEFT JOIN users u ON u.id = c.author_id
                        WHERE c.post_id = :postId
                          AND NOT EXISTS (SELECT 1 FROM user_blocks b
                                          WHERE (b.blocker_id = :me AND b.blocked_id = c.author_id)
                                             OR (b.blocker_id = c.author_id AND b.blocked_id = :me))
                        ORDER BY c.id
                        """)
                .param("me", me)
                .param("postId", postId)
                .query((rs, rowNum) -> {
                    long authorId = rs.getLong("author_id");
                    boolean hasAuthor = !rs.wasNull();
                    long parentId = rs.getLong("parent_id");
                    boolean hasParent = !rs.wasNull();
                    return new CommunityDtos.CommentItem(
                            rs.getLong("id"),
                            hasParent ? parentId : null,
                            hasAuthor ? new CommunityDtos.Author(authorId, rs.getString("nickname"),
                                    rs.getString("profile_image_url"), rs.getBigDecimal("manner_score")) : null,
                            rs.getString("content"),
                            rs.getBoolean("deleted"),
                            hasAuthor && authorId == me,
                            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                            List.of());
                })
                .list();
    }

    private List<CommunityDtos.PostItem> attachImages(List<CommunityDtos.PostItem> posts) {
        if (posts.isEmpty()) {
            return posts;
        }
        Map<Long, List<CommunityDtos.Image>> byPost = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT post_id, url, width, height FROM post_images
                        WHERE post_id IN (:ids)
                        ORDER BY post_id, sort_order
                        """)
                .param("ids", posts.stream().map(CommunityDtos.PostItem::id).toList())
                .query(rs -> {
                    byPost.computeIfAbsent(rs.getLong("post_id"), id -> new ArrayList<>())
                            .add(new CommunityDtos.Image(rs.getString("url"), rs.getInt("width"), rs.getInt("height")));
                });
        return posts.stream().map(post -> post.withImages(byPost.getOrDefault(post.id(), List.of()))).toList();
    }

    private static CommunityDtos.PostItem mapPost(ResultSet rs, Long me) throws SQLException {
        long authorId = rs.getLong("author_id");
        short sportId = rs.getShort("sport_id");
        boolean hasSport = !rs.wasNull();
        return new CommunityDtos.PostItem(
                rs.getLong("id"),
                Post.Category.valueOf(rs.getString("category")),
                hasSport ? sportId : null,
                rs.getString("sport_code"),
                rs.getString("sport_name"),
                rs.getString("content"),
                List.of(),
                new CommunityDtos.Author(authorId, rs.getString("nickname"), rs.getString("profile_image_url"),
                        rs.getBigDecimal("manner_score")),
                rs.getString("area_name"),
                rs.getInt("like_count"),
                rs.getInt("comment_count"),
                rs.getBoolean("liked"),
                authorId == me,
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at") != null);
    }
}

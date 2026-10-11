package com.fitmate.domain.guest;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 체험 계정이 다른 사람(데모 사용자 등)의 데이터에 남긴 흔적을 되돌린다. 계정을 지우기 직전에 같은 트랜잭션에서 부른다.
 * 회원 탈퇴는 상대방 채팅·댓글을 "탈퇴한 회원"으로 남기고 숫자(참가 인원·좋아요 수·매너 온도)도 그대로 두지만,
 * 체험 계정은 누구나 만들 수 있으므로 체험하기 전 상태로 돌려놓아 데모 데이터가 체험할수록 망가지지 않게 한다.
 * (체험 계정 자신의 글·모임·요청·채팅방 참여는 이후 계정 삭제 때 ON DELETE CASCADE로 지워진다)
 */
@Repository
@RequiredArgsConstructor
class GuestTraceCleaner {

    private final JdbcClient jdbcClient;

    void revert(Long guestId, String nickname) {
        releaseGatheringSeats(guestId);
        removeChats(guestId, nickname);
        removeLikes(guestId);
        removeComments(guestId);
        revertMannerReviews(guestId);
        jdbcClient.sql("DELETE FROM user_reports WHERE reporter_id = :id").param("id", guestId).update();
        // 알림에는 보낸 사람 칸이 없어서, 체험 계정 닉네임(체험러1234)이 들어간 알림을 지운다 (참여·요청·댓글 알림)
        jdbcClient.sql("DELETE FROM notifications WHERE title LIKE :pattern OR body LIKE :pattern")
                .param("pattern", "%" + nickname + "님%")
                .update();
    }

    /** 참가했던 남의 모임에 자리를 돌려주고, 그 때문에 마감됐던 앞으로의 모임은 다시 모집 중으로 연다 */
    private void releaseGatheringSeats(Long guestId) {
        jdbcClient.sql("""
                        UPDATE gatherings g
                        SET current_count = g.current_count - 1,
                            status = CASE WHEN g.status = 'CLOSED' AND g.starts_at > now() THEN 'RECRUITING' ELSE g.status END
                        FROM gathering_participants p
                        WHERE p.gathering_id = g.id
                          AND p.user_id = :id
                          AND p.status <> 'CANCELED'
                          AND g.host_id <> :id
                          AND g.current_count > 1
                        """)
                .param("id", guestId)
                .update();
    }

    /** 1:1 채팅방은 통째로, 단체방에서는 보낸 메시지와 "OO님이 들어왔어요/나갔어요" 안내를 지운다 */
    private void removeChats(Long guestId, String nickname) {
        jdbcClient.sql("""
                        DELETE FROM chat_rooms
                        WHERE type = 'DIRECT'
                          AND id IN (SELECT room_id FROM chat_room_members WHERE user_id = :id)
                        """)
                .param("id", guestId)
                .update();
        jdbcClient.sql("DELETE FROM chat_messages WHERE sender_id = :id").param("id", guestId).update();
        jdbcClient.sql("""
                        DELETE FROM chat_messages m
                        USING chat_rooms r
                        WHERE m.room_id = r.id
                          AND r.type = 'GATHERING'
                          AND m.message_type = 'SYSTEM'
                          AND m.content IN (:joined, :left)
                        """)
                .param("joined", nickname + "님이 들어왔어요")
                .param("left", nickname + "님이 나갔어요")
                .update();
    }

    private void removeLikes(Long guestId) {
        jdbcClient.sql("""
                        UPDATE posts
                        SET like_count = like_count - 1
                        WHERE id IN (SELECT post_id FROM post_likes WHERE user_id = :id)
                          AND like_count > 0
                        """)
                .param("id", guestId)
                .update();
        jdbcClient.sql("DELETE FROM post_likes WHERE user_id = :id").param("id", guestId).update();
    }

    /**
     * 댓글을 지우고(달린 답글도 함께) 그 글들의 댓글 수를 다시 센다.
     * 답글만 남아 있던 "삭제된 댓글" 자리는 답글이 모두 사라지면 함께 지운다 (CommunityService.deleteComment와 같은 규칙)
     */
    private void removeComments(Long guestId) {
        List<Long> postIds = jdbcClient.sql("""
                        WITH removed AS (DELETE FROM comments WHERE author_id = :id RETURNING post_id)
                        SELECT DISTINCT post_id FROM removed
                        """)
                .param("id", guestId)
                .query(Long.class)
                .list();
        if (postIds.isEmpty()) {
            return;
        }
        jdbcClient.sql("""
                        DELETE FROM comments c
                        WHERE c.post_id IN (:postIds)
                          AND c.deleted
                          AND NOT EXISTS (SELECT 1 FROM comments r WHERE r.parent_id = c.id)
                        """)
                .param("postIds", postIds)
                .update();
        jdbcClient.sql("""
                        UPDATE posts p
                        SET comment_count = (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id AND NOT c.deleted)
                        WHERE p.id IN (:postIds)
                        """)
                .param("postIds", postIds)
                .update();
    }

    /** 체험 계정이 남긴 매너 평가만큼 상대 매너 온도를 되돌리고 평가를 지운다 */
    private void revertMannerReviews(Long guestId) {
        jdbcClient.sql("""
                        UPDATE users u
                        SET manner_score = LEAST(99.9, GREATEST(0, u.manner_score - r.total))
                        FROM (SELECT target_id, SUM(score_delta) AS total
                              FROM manner_reviews WHERE reviewer_id = :id GROUP BY target_id) r
                        WHERE u.id = r.target_id
                        """)
                .param("id", guestId)
                .update();
        jdbcClient.sql("DELETE FROM manner_reviews WHERE reviewer_id = :id").param("id", guestId).update();
    }
}

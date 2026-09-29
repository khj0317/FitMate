package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 채팅방 목록은 방마다 상대방, 마지막 메시지, 안 읽은 수가 필요해서
 * JPA로 N+1 없이 만들기 어렵다. 한 번의 SQL로 가져온다.
 */
@Repository
@RequiredArgsConstructor
public class ChatRoomQuery {

    private static final String SQL = """
            SELECT r.id AS room_id,
                   r.type,
                   other.id AS other_id,
                   other.nickname AS other_nickname,
                   other.profile_image_url AS other_image,
                   lm.id AS last_message_id,
                   CASE WHEN lm.message_type = 'IMAGE' THEN '📷 사진' ELSE lm.content END AS last_message_content,
                   lm.created_at AS last_message_at,
                   (SELECT COUNT(*)
                    FROM chat_messages cm
                    WHERE cm.room_id = r.id
                      AND cm.id > COALESCE(me.last_read_message_id, 0)
                      AND cm.sender_id IS DISTINCT FROM me.user_id) AS unread_count,
                   -- 1:1 방은 상대가 탈퇴했거나 차단 관계면 보낼 수 없다
                   (r.type <> 'DIRECT' OR (other.id IS NOT NULL AND NOT EXISTS (
                        SELECT 1 FROM user_blocks b
                        WHERE (b.blocker_id = me.user_id AND b.blocked_id = other.id)
                           OR (b.blocker_id = other.id AND b.blocked_id = me.user_id)))) AS can_send,
                   g.title AS title,
                   r.gathering_id,
                   (SELECT COUNT(*) FROM chat_room_members cnt WHERE cnt.room_id = r.id) AS member_count
            FROM chat_room_members me
            JOIN chat_rooms r ON r.id = me.room_id
            LEFT JOIN chat_room_members om
                   ON r.type = 'DIRECT' AND om.room_id = r.id AND om.user_id <> me.user_id
            LEFT JOIN users other ON other.id = om.user_id
            LEFT JOIN gatherings g ON g.id = r.gathering_id
            LEFT JOIN LATERAL (
                SELECT id, content, message_type, created_at
                FROM chat_messages
                WHERE room_id = r.id
                ORDER BY id DESC
                LIMIT 1
            ) lm ON TRUE
            WHERE me.user_id = :userId
              -- 내가 차단한 사람과의 방은 목록에서 숨긴다
              AND NOT EXISTS (
                  SELECT 1 FROM user_blocks b WHERE b.blocker_id = me.user_id AND b.blocked_id = om.user_id
              )
            ORDER BY COALESCE(lm.id, 0) DESC, r.id DESC
            """;

    private final JdbcClient jdbcClient;

    public List<ChatDtos.Room> findRooms(Long userId) {
        return jdbcClient.sql(SQL)
                .param("userId", userId)
                .query((rs, rowNum) -> new ChatDtos.Room(
                        rs.getLong("room_id"),
                        ChatRoomType.valueOf(rs.getString("type")),
                        counterpart(rs),
                        lastMessage(rs),
                        rs.getLong("unread_count"),
                        rs.getBoolean("can_send"),
                        rs.getString("title"),
                        rs.getObject("gathering_id", Long.class),
                        rs.getInt("member_count")))
                .list();
    }

    private static ChatDtos.Counterpart counterpart(ResultSet rs) throws SQLException {
        long id = rs.getLong("other_id");
        return rs.wasNull() ? null
                : new ChatDtos.Counterpart(id, rs.getString("other_nickname"), rs.getString("other_image"));
    }

    private static ChatDtos.LastMessage lastMessage(ResultSet rs) throws SQLException {
        long id = rs.getLong("last_message_id");
        return rs.wasNull() ? null
                : new ChatDtos.LastMessage(id, rs.getString("last_message_content"),
                        rs.getObject("last_message_at", OffsetDateTime.class).toInstant());
    }
}

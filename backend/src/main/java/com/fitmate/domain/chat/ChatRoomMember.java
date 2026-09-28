package com.fitmate.domain.chat;

import com.fitmate.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "chat_room_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("roomId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Long lastReadMessageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    ChatRoomMember(ChatRoom room, User user) {
        this.room = room;
        this.user = user;
    }

    /**
     * 늦게 도착한 읽음 요청이 더 최신 값을 덮어쓰지 않도록 앞으로만 이동시킨다.
     * @return 실제로 이동했으면 true
     */
    public boolean markRead(Long messageId) {
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            lastReadMessageId = messageId;
            return true;
        }
        return false;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {
        private Long roomId;
        private Long userId;
    }
}

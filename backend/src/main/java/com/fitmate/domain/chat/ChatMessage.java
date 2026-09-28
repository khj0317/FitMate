package com.fitmate.domain.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 메시지는 양이 가장 많은 테이블이라 연관관계 대신 ID만 들고, 조회는 인덱스(room_id, id DESC)로 한다.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    private Long senderId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ChatMessage(Long roomId, Long senderId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
    }
}

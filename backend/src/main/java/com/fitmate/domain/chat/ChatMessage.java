package com.fitmate.domain.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 텍스트 메시지는 content, 사진 메시지는 imageUrl을 가진다 (DB CHECK 제약으로도 보장).
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

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private MessageType type;

    @Column(length = MAX_LENGTH)
    private String content;

    @Column(length = 500)
    private String imageUrl;

    private Short imageWidth;

    private Short imageHeight;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ChatMessage(Long roomId, Long senderId, String content) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.type = MessageType.TEXT;
        this.content = content;
    }

    /** 단체방 입장·퇴장 같은 안내. 보낸 사람이 없고, 안 읽은 수에 세지 않는다 */
    public static ChatMessage system(Long roomId, String content) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.type = MessageType.SYSTEM;
        message.content = content;
        return message;
    }

    public static ChatMessage image(Long roomId, Long senderId, String imageUrl, int width, int height) {
        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.senderId = senderId;
        message.type = MessageType.IMAGE;
        message.imageUrl = imageUrl;
        message.imageWidth = (short) width;
        message.imageHeight = (short) height;
        return message;
    }

    public enum MessageType {
        TEXT,
        IMAGE,
        SYSTEM
    }
}

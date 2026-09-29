package com.fitmate.domain.chat.dto;

import com.fitmate.domain.chat.ChatMessage;
import com.fitmate.domain.chat.ChatRoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record SendMessage(
            @NotBlank @Size(max = ChatMessage.MAX_LENGTH, message = "메시지는 1000자 이하여야 합니다.") String content
    ) {
    }

    public record MarkRead(@NotNull @Positive Long lastMessageId) {
    }

    /** REST 응답과 WebSocket 브로드캐스트에 같은 형식을 쓴다. 사진 메시지는 content 대신 image* 필드를 쓴다. */
    public record Message(
            Long id,
            Long roomId,
            Long senderId,
            String senderNickname,
            ChatMessage.MessageType type,
            String content,
            String imageUrl,
            Short imageWidth,
            Short imageHeight,
            Instant createdAt
    ) {
        public static Message of(ChatMessage message, String senderNickname) {
            return new Message(message.getId(), message.getRoomId(), message.getSenderId(), senderNickname,
                    message.getType(), message.getContent(), message.getImageUrl(),
                    message.getImageWidth(), message.getImageHeight(), message.getCreatedAt());
        }
    }

    /**
     * nextCursor가 null이면 더 오래된 메시지가 없다.
     * otherLastReadMessageId: 상대가 읽은 마지막 메시지 ID (null이면 아직 하나도 안 읽음)
     */
    public record MessagePage(List<Message> messages, Long nextCursor, Long otherLastReadMessageId,
                              List<ReadCursor> readCursors) {
    }

    /** 멤버가 읽은 마지막 메시지 ID (null이면 아직 하나도 안 읽음) */
    public record ReadCursor(Long userId, Long lastReadMessageId) {
    }

    public record Room(
            Long roomId,
            ChatRoomType type,
            Counterpart counterpart,
            LastMessage lastMessage,
            long unreadCount,
            boolean canSend,
            /* 단체방(모임) 이름, 1:1 방은 null */
            String title,
            Long gatheringId,
            int memberCount
    ) {
    }

    public record Counterpart(Long userId, String nickname, String profileImageUrl) {
    }

    public record LastMessage(Long id, String content, Instant createdAt) {
    }
}

package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Redis 채널로 들어온 메시지·읽음 위치를 이 서버에 연결된 WebSocket 구독자에게 전달한다.
 * - 메시지: /topic/chat-rooms/{roomId}
 * - 읽음:   /topic/chat-rooms/{roomId}/reads
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSubscriber implements MessageListener {

    public static final String ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";
    public static final String READS_SUFFIX = "/reads";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            if (ChatMessagePublisher.READ_CHANNEL.equals(channel)) {
                ChatReadEvent read = objectMapper.readValue(json, ChatReadEvent.class);
                messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + read.roomId() + READS_SUFFIX, read);
            } else {
                ChatDtos.Message chatMessage = objectMapper.readValue(json, ChatDtos.Message.class);
                messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + chatMessage.roomId(), chatMessage);
            }
        } catch (RuntimeException e) {
            // 메시지 하나가 잘못돼도 구독 리스너가 멈추지 않도록 로그만 남긴다
            log.error("Failed to deliver chat event from Redis", e);
        }
    }
}

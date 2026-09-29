package com.fitmate.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 알림 실시간 전달. 받을 사람이 어느 서버에 연결돼 있는지 모르므로 Redis로 모든 서버에 알리고,
 * 각 서버는 자기에게 연결된 경우에만 전달된다 (/user/{userId}/queue/notifications).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRelay implements MessageListener {

    public static final String CHANNEL = "notifications";
    public static final String USER_DESTINATION = "/queue/notifications";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationService.NotificationCreatedEvent event) {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationService.NotificationCreatedEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), NotificationService.NotificationCreatedEvent.class);
            messagingTemplate.convertAndSendToUser(String.valueOf(event.userId()), USER_DESTINATION, event.item());
        } catch (RuntimeException e) {
            log.error("Failed to deliver notification", e);
        }
    }
}

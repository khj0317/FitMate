package com.fitmate.domain.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * 저장된 메시지를 Redis 채널로 발행한다.
 * 서버가 여러 대면 A 서버에 연결된 사용자가 보낸 메시지를 B 서버에 연결된 사용자도 받아야 하므로,
 * 모든 서버가 이 채널을 구독하고(ChatMessageSubscriber) 각자 자기에게 연결된 클라이언트에게 전달한다.
 */
@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    public static final String CHANNEL = "chat:messages";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatMessageSavedEvent event) {
        redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event.message()));
    }
}

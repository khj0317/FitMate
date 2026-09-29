package com.fitmate.global.config;

import com.fitmate.domain.chat.ChatMessagePublisher;
import com.fitmate.domain.chat.ChatMessageSubscriber;
import com.fitmate.domain.notification.NotificationRelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;

/** 여러 서버가 실시간 이벤트(채팅, 읽음, 알림)를 주고받는 Redis Pub/Sub 구독 설정 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       ChatMessageSubscriber chatMessageSubscriber,
                                                                       NotificationRelay notificationRelay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageSubscriber, List.of(
                new ChannelTopic(ChatMessagePublisher.CHANNEL),
                new ChannelTopic(ChatMessagePublisher.READ_CHANNEL)));
        container.addMessageListener(notificationRelay, new ChannelTopic(NotificationRelay.CHANNEL));
        return container;
    }
}

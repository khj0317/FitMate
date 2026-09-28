package com.fitmate.global.config;

import com.fitmate.domain.chat.ChatMessagePublisher;
import com.fitmate.domain.chat.ChatMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       ChatMessageSubscriber chatMessageSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatMessageSubscriber, List.of(
                new ChannelTopic(ChatMessagePublisher.CHANNEL),
                new ChannelTopic(ChatMessagePublisher.READ_CHANNEL)));
        return container;
    }
}

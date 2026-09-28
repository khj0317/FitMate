package com.fitmate.global.websocket;

import com.fitmate.domain.chat.ChatMessageSubscriber;
import com.fitmate.domain.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * 브라우저 WebSocket API는 핸드셰이크에 Authorization 헤더를 붙일 수 없어서,
 * STOMP CONNECT 프레임의 헤더로 JWT를 받아 인증한다.
 * - CONNECT  : 토큰 검증 후 세션에 사용자 등록
 * - SUBSCRIBE: 채팅방 토픽은 그 방의 멤버만 구독 가능
 * - SEND     : 인증된 세션만 가능
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> requireUser(accessor);
            default -> {
            }
        }
        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("UNAUTHORIZED");
        }
        try {
            String userId = jwtDecoder.decode(header.substring(BEARER_PREFIX.length())).getSubject();
            return new StompPrincipal(userId);
        } catch (JwtException e) {
            throw new MessageDeliveryException("INVALID_TOKEN");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = requireUser(accessor);
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(ChatMessageSubscriber.ROOM_TOPIC_PREFIX)) {
            Long roomId = parseRoomId(destination);
            if (roomId == null || !chatService.isMember(roomId, Long.valueOf(user.getName()))) {
                throw new MessageDeliveryException("CHAT_ROOM_NOT_FOUND");
            }
        }
    }

    private Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessageDeliveryException("UNAUTHORIZED");
        }
        return user;
    }

    /** /topic/chat-rooms/{roomId} 또는 /topic/chat-rooms/{roomId}/reads 에서 방 ID를 꺼낸다 */
    private static Long parseRoomId(String destination) {
        String rest = destination.substring(ChatMessageSubscriber.ROOM_TOPIC_PREFIX.length());
        if (rest.endsWith(ChatMessageSubscriber.READS_SUFFIX)) {
            rest = rest.substring(0, rest.length() - ChatMessageSubscriber.READS_SUFFIX.length());
        }
        try {
            return Long.valueOf(rest);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}

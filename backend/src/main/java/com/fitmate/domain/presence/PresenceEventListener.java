package com.fitmate.domain.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/** WebSocket(STOMP) 연결·끊김을 접속 상태에 반영한다. 인증은 StompAuthInterceptor가 CONNECT 때 끝낸 상태다. */
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user != null && sessionId != null) {
            presenceService.connected(sessionId, Long.valueOf(user.getName()));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        presenceService.disconnected(event.getSessionId());
    }
}

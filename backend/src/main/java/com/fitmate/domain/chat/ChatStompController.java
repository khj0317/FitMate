package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.error.ErrorResponse;
import com.fitmate.global.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final RateLimiter rateLimiter;

    /** 저장 후 브로드캐스트는 Redis를 거쳐 ChatMessageSubscriber가 한다. */
    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void send(@DestinationVariable Long roomId, @Payload ChatDtos.SendMessage request, Principal principal) {
        // REST 전송과 같은 한도를 공유해서, 경로를 바꿔 도배하는 것을 막는다 (넘으면 /user/queue/errors로 안내)
        rateLimiter.check("chat-message", "user:" + principal.getName(), 60, 60);
        chatService.send(roomId, Long.valueOf(principal.getName()), request.content());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleBusiness(BusinessException e) {
        return new ErrorResponse(e.getErrorCode().getStatus().value(), e.getErrorCode().name(), e.getMessage(), List.of());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorResponse handleUnexpected(Exception e) {
        return ErrorResponse.of(ErrorCode.INVALID_INPUT);
    }
}

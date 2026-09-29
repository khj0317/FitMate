package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.error.ErrorResponse;
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

    /** 저장 후 브로드캐스트는 Redis를 거쳐 ChatMessageSubscriber가 한다. */
    @MessageMapping("/chat-rooms/{roomId}/messages")
    public void send(@DestinationVariable Long roomId, @Payload ChatDtos.SendMessage request, Principal principal) {
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

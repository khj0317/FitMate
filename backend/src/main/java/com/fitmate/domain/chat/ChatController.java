package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "채팅")
@RestController
@RequestMapping("/api/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록", description = "상대방, 마지막 메시지, 안 읽은 메시지 수를 포함하며 최근 대화 순으로 정렬합니다.")
    @GetMapping
    public List<ChatDtos.Room> getRooms(@Parameter(hidden = true) @LoginUserId Long userId) {
        return chatService.getRooms(userId);
    }

    @Operation(summary = "메시지 목록", description = "최신순으로 반환합니다. 이전 메시지는 응답의 nextCursor를 cursor로 넘겨 조회합니다.")
    @GetMapping("/{roomId}/messages")
    public ChatDtos.MessagePage getMessages(@Parameter(hidden = true) @LoginUserId Long userId,
                                            @PathVariable Long roomId,
                                            @RequestParam(required = false) Long cursor,
                                            @RequestParam(defaultValue = "30") int size) {
        return chatService.getMessages(roomId, userId, cursor, size);
    }

    @Operation(summary = "메시지 보내기 (REST)",
            description = "WebSocket을 쓸 수 없을 때를 위한 API입니다. 보낸 메시지는 WebSocket 구독자에게도 전달됩니다.")
    @PostMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDtos.Message send(@Parameter(hidden = true) @LoginUserId Long userId,
                                 @PathVariable Long roomId,
                                 @Valid @RequestBody ChatDtos.SendMessage request) {
        return chatService.send(roomId, userId, request.content());
    }

    @Operation(summary = "읽음 처리", description = "이 메시지까지 읽었다고 기록합니다. 이전 값보다 작으면 무시합니다.")
    @PostMapping("/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@Parameter(hidden = true) @LoginUserId Long userId,
                         @PathVariable Long roomId,
                         @Valid @RequestBody ChatDtos.MarkRead request) {
        chatService.markRead(roomId, userId, request.lastMessageId());
    }
}

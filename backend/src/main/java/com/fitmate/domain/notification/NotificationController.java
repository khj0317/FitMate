package com.fitmate.domain.notification;

import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림 목록", description = "최신순 30개. 실시간 알림은 WebSocket /user/queue/notifications 로도 옵니다.")
    @GetMapping
    public NotificationService.Page list(@Parameter(hidden = true) @LoginUserId Long userId,
                                         @RequestParam(required = false) Long cursor) {
        return notificationService.list(userId, cursor);
    }

    @Operation(summary = "알림 읽음")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
    }

    @Operation(summary = "알림 모두 읽음")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@Parameter(hidden = true) @LoginUserId Long userId) {
        notificationService.markAllRead(userId);
    }
}

package com.fitmate.domain.safety;

import com.fitmate.global.security.LoginUserId;
import com.fitmate.global.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "차단 · 신고")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @Operation(summary = "차단", description = "서로 추천에 보이지 않고, 매칭 요청·채팅을 주고받을 수 없습니다. 대기 중인 요청은 취소됩니다.")
    @PutMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long userId) {
        safetyService.block(me, userId);
    }

    @Operation(summary = "차단 해제")
    @DeleteMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long userId) {
        safetyService.unblock(me, userId);
    }

    @Operation(summary = "내가 차단한 사용자 목록")
    @GetMapping("/me/blocks")
    public List<SafetyService.BlockedUser> blocks(@Parameter(hidden = true) @LoginUserId Long me) {
        return safetyService.getBlocks(me);
    }

    @Operation(summary = "신고", description = "운영자가 검토합니다. block=true면 함께 차단합니다.")
    @RateLimited(name = "report", limit = 10, windowSeconds = 3600)
    @PostMapping("/{userId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long userId,
                       @Valid @RequestBody ReportRequest request) {
        safetyService.report(me, userId, request.reason(), request.detail(), request.block());
    }

    public record ReportRequest(
            @NotNull(message = "신고 사유를 선택해 주세요.") UserReport.ReportReason reason,
            @Size(max = 500, message = "내용은 500자 이하여야 합니다.") String detail,
            boolean block
    ) {
    }
}

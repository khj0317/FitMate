package com.fitmate.domain.matchrequest;

import com.fitmate.domain.matchrequest.dto.MatchRequestDtos;
import com.fitmate.global.security.LoginUserId;
import com.fitmate.global.ratelimit.RateLimited;
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

@Tag(name = "매칭 요청")
@RestController
@RequestMapping("/api/match-requests")
@RequiredArgsConstructor
public class MatchRequestController {

    private final MatchRequestService matchRequestService;

    @Operation(summary = "매칭 요청 보내기", description = "상대가 등록한 운동 종목으로만 요청할 수 있습니다.")
    @RateLimited(name = "match-request", limit = 30, windowSeconds = 3600)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchRequestDtos.Created create(@Parameter(hidden = true) @LoginUserId Long userId,
                                           @Valid @RequestBody MatchRequestDtos.Create request) {
        return matchRequestService.create(userId, request);
    }

    @Operation(summary = "받은 매칭 요청 목록")
    @GetMapping("/received")
    public List<MatchRequestDtos.Response> getReceived(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @RequestParam(defaultValue = "PENDING") MatchRequestStatus status) {
        return matchRequestService.getReceived(userId, status);
    }

    @Operation(summary = "보낸 매칭 요청 목록")
    @GetMapping("/sent")
    public List<MatchRequestDtos.Response> getSent(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @RequestParam(defaultValue = "PENDING") MatchRequestStatus status) {
        return matchRequestService.getSent(userId, status);
    }

    @Operation(summary = "매칭 요청 수락", description = "수락하면 1:1 채팅방이 만들어지고 chatRoomId를 반환합니다.")
    @PostMapping("/{requestId}/accept")
    public MatchRequestDtos.Accepted accept(@Parameter(hidden = true) @LoginUserId Long userId,
                                            @PathVariable Long requestId) {
        return matchRequestService.accept(userId, requestId);
    }

    @Operation(summary = "매칭 요청 거절")
    @PostMapping("/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long requestId) {
        matchRequestService.reject(userId, requestId);
    }

    @Operation(summary = "보낸 매칭 요청 취소")
    @PostMapping("/{requestId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long requestId) {
        matchRequestService.cancel(userId, requestId);
    }
}

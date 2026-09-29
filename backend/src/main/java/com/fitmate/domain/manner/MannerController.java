package com.fitmate.domain.manner;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "매너 평가")
@RestController
@RequiredArgsConstructor
public class MannerController {

    private final MannerService mannerService;

    @Operation(summary = "평가할 상대 목록", description = "끝난 모임의 참가자(14일 이내)와 수락된 1:1 매칭 상대(30일 이내) 중 아직 평가하지 않은 사람")
    @GetMapping("/api/manner/pending")
    public List<MannerDtos.Pending> pending(@Parameter(hidden = true) @LoginUserId Long me) {
        return mannerService.pending(me);
    }

    @Operation(summary = "매너 평가 남기기", description = "GOOD +0.5, NORMAL 0, BAD -0.5, 노쇼 태그는 추가로 -1.0")
    @PostMapping("/api/manner/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public void review(@Parameter(hidden = true) @LoginUserId Long me,
                       @Valid @RequestBody MannerDtos.CreateReview request) {
        mannerService.review(me, request);
    }

    @Operation(summary = "매너 정보", description = "매너 온도, 받은 평가 수, 칭찬 태그 수 (아쉬운 태그는 공개하지 않음)")
    @GetMapping("/api/users/{userId}/manner")
    public MannerDtos.Summary summary(@PathVariable Long userId) {
        return mannerService.summary(userId);
    }
}

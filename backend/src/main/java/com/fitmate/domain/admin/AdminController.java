package com.fitmate.domain.admin;

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

@Tag(name = "관리자")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "운영 현황", description = "가입자, 최근 7일 가입·글·메시지, 매칭, 모임, 대기 신고, 정지 계정 수")
    @GetMapping("/stats")
    public AdminDtos.Stats stats(@Parameter(hidden = true) @LoginUserId Long me) {
        return adminService.stats(me);
    }

    @Operation(summary = "신고 목록", description = "pending=true 검토 대기(기본) / false 처리 완료")
    @GetMapping("/reports")
    public AdminDtos.ReportPage reports(@Parameter(hidden = true) @LoginUserId Long me,
                                        @RequestParam(defaultValue = "true") boolean pending,
                                        @RequestParam(required = false) Long cursor) {
        return adminService.reports(me, pending, cursor);
    }

    @Operation(summary = "신고 처리", description = "DISMISS(문제 없음) / WARN(경고 알림) / SUSPEND_7D / SUSPEND_PERMANENT. 같은 사람의 대기 신고도 함께 처리")
    @PostMapping("/reports/{reportId}/resolve")
    public AdminDtos.Resolved resolve(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long reportId,
                                      @Valid @RequestBody AdminDtos.Resolve request) {
        return adminService.resolve(me, reportId, request);
    }

    @Operation(summary = "정지 해제")
    @PostMapping("/users/{userId}/unsuspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsuspend(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long userId) {
        adminService.unsuspend(me, userId);
    }

    @Operation(summary = "글 숨기기", description = "피드·상세에서 보이지 않게 한다 (삭제하지 않아 되돌릴 수 있음)")
    @PostMapping("/posts/{postId}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hidePost(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        adminService.setPostHidden(me, postId, true);
    }

    @Operation(summary = "글 숨김 해제")
    @PostMapping("/posts/{postId}/unhide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unhidePost(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        adminService.setPostHidden(me, postId, false);
    }
}

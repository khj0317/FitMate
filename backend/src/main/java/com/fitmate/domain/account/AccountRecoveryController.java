package com.fitmate.domain.account;

import com.fitmate.domain.auth.dto.AuthRequests;
import com.fitmate.global.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계정 찾기")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountRecoveryController {

    private final AccountRecoveryService recoveryService;

    @Operation(summary = "아이디 찾기",
            description = "가입할 때 등록한 이메일로 아이디를 보냅니다. 가입 여부와 상관없이 항상 202를 반환합니다.")
    @RateLimited(name = "account-recovery", limit = 20, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/find-login-id")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void findLoginId(@Valid @RequestBody FindLoginId request) {
        recoveryService.sendLoginId(request.email());
    }

    @Operation(summary = "비밀번호 재설정 코드 요청",
            description = "아이디와 등록한 이메일이 일치하면 6자리 인증 코드(10분 유효)를 보냅니다. 항상 202를 반환합니다.")
    @RateLimited(name = "account-recovery", limit = 20, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        recoveryService.sendPasswordResetCode(request.loginId(), request.email());
    }

    @Operation(summary = "비밀번호 재설정",
            description = "인증 코드가 맞으면 비밀번호를 바꾸고 모든 기기에서 로그아웃합니다. 코드는 5번 틀리면 폐기됩니다.")
    @RateLimited(name = "password-reset-confirm", limit = 20, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm request) {
        recoveryService.resetPassword(request.loginId(), request.code(), request.newPassword(), request.newPasswordConfirm());
    }

    public record FindLoginId(
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "올바른 이메일 형식이 아닙니다.") String email
    ) {
    }

    public record PasswordResetRequest(
            @NotBlank(message = "아이디를 입력해 주세요.") String loginId,
            @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "올바른 이메일 형식이 아닙니다.") String email
    ) {
    }

    public record PasswordResetConfirm(
            @NotBlank(message = "아이디를 입력해 주세요.") String loginId,
            @NotBlank(message = "인증 코드를 입력해 주세요.") @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "인증 코드는 6자리 숫자입니다.") String code,
            @NotBlank(message = "새 비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
            @Pattern(regexp = AuthRequests.PASSWORD_PATTERN, message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String newPassword,
            @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.") String newPasswordConfirm
    ) {
    }
}

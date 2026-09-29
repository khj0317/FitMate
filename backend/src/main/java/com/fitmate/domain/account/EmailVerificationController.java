package com.fitmate.domain.account;

import com.fitmate.global.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이메일 인증")
@RestController
@RequestMapping("/api/auth/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public record SendCode(
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 255) String email) {
    }

    public record Confirm(@NotBlank @Email String email, @NotBlank(message = "인증 코드를 입력해 주세요.") String code) {
    }

    public record Verified(String verificationToken) {
    }

    @Operation(summary = "인증 코드 보내기", description = "가입·이메일 변경 전에 6자리 코드를 메일로 보냅니다 (같은 이메일은 1분에 한 번)")
    @RateLimited(name = "email-verification", limit = 20, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@Valid @RequestBody SendCode request) {
        emailVerificationService.sendCode(request.email());
    }

    @Operation(summary = "인증 코드 확인", description = "맞으면 30분 동안 쓸 수 있는 인증 토큰을 줍니다. 가입·이메일 변경 요청에 emailVerificationToken으로 보내세요")
    @RateLimited(name = "email-verification-confirm", limit = 30, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/confirm")
    public Verified confirm(@Valid @RequestBody Confirm request) {
        return new Verified(emailVerificationService.confirm(request.email(), request.code()));
    }
}

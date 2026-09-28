package com.fitmate.domain.auth;

import com.fitmate.domain.auth.dto.AuthRequests;
import com.fitmate.domain.auth.dto.AuthResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@SecurityRequirements // 인증 API는 토큰 없이 호출
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponses.Signup signup(@Valid @RequestBody AuthRequests.Signup request) {
        return authService.signup(request);
    }

    @Operation(summary = "로그인", description = "액세스 토큰(30분)과 리프레시 토큰(14일)을 발급합니다.")
    @PostMapping("/login")
    public AuthResponses.Token login(@Valid @RequestBody AuthRequests.Login request) {
        return authService.login(request);
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰은 한 번만 사용할 수 있으며, 새 리프레시 토큰으로 교체됩니다.")
    @PostMapping("/refresh")
    public AuthResponses.Token refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기합니다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthRequests.Refresh request) {
        authService.logout(request);
    }
}

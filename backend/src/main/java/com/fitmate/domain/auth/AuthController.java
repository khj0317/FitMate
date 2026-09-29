package com.fitmate.domain.auth;

import com.fitmate.domain.auth.dto.AuthRequests;
import com.fitmate.domain.auth.dto.AuthResponses;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.ratelimit.RateLimited;
import com.fitmate.global.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리프레시 토큰은 두 가지 방식으로 주고받는다.
 * - 웹(X-Auth-Mode: cookie): HttpOnly 쿠키. 응답 본문의 refreshToken은 null
 * - 앱·기타 클라이언트: 지금처럼 응답 본문과 요청 본문
 */
@Tag(name = "인증")
@SecurityRequirements // 인증 API는 토큰 없이 호출
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final ClientIp clientIp;

    @Operation(summary = "회원가입")
    @RateLimited(name = "signup", limit = 10, windowSeconds = 3600, key = RateLimited.Key.IP)
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponses.Signup signup(@Valid @RequestBody AuthRequests.Signup request) {
        return authService.signup(request);
    }

    @Operation(summary = "로그인", description = "액세스 토큰(30분)과 리프레시 토큰(14일)을 발급합니다. "
            + "X-Auth-Mode: cookie 헤더를 보내면 리프레시 토큰은 HttpOnly 쿠키로만 줍니다.")
    @PostMapping("/login")
    public AuthResponses.Token login(@Valid @RequestBody AuthRequests.Login request,
                                     HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthResponses.Token tokens = authService.login(request, clientIp.of(servletRequest));
        return deliver(tokens, servletRequest, servletResponse);
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰(본문 또는 쿠키)은 한 번만 쓸 수 있고 새 토큰으로 교체됩니다.")
    @PostMapping("/refresh")
    public AuthResponses.Token refresh(@RequestBody(required = false) AuthRequests.Refresh request,
                                       HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        String token = refreshTokenOf(request, servletRequest);
        try {
            return deliver(authService.refresh(new AuthRequests.Refresh(token)), servletRequest, servletResponse);
        } catch (BusinessException e) {
            // 쓸 수 없는 쿠키는 지워서 브라우저가 계속 보내지 않게 한다
            if (refreshTokenCookie.wanted(servletRequest)) {
                refreshTokenCookie.clear(servletResponse);
            }
            throw e;
        }
    }

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기하고 쿠키를 지웁니다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) AuthRequests.Refresh request,
                       HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        refreshTokenCookie.clear(servletResponse);
        String token = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken() : refreshTokenCookie.read(servletRequest).orElse(null);
        if (token != null) {
            authService.logout(new AuthRequests.Refresh(token));
        }
    }

    /** 웹이면 리프레시 토큰을 쿠키로 옮기고 본문에서는 뺀다 */
    private AuthResponses.Token deliver(AuthResponses.Token tokens, HttpServletRequest request, HttpServletResponse response) {
        if (!refreshTokenCookie.wanted(request)) {
            return tokens;
        }
        refreshTokenCookie.write(response, tokens.refreshToken());
        return AuthResponses.Token.bearer(tokens.accessToken(), null, tokens.expiresIn());
    }

    private String refreshTokenOf(AuthRequests.Refresh request, HttpServletRequest servletRequest) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return refreshTokenCookie.read(servletRequest)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
    }
}

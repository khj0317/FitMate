package com.fitmate.domain.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 웹 브라우저용: 리프레시 토큰을 JavaScript가 읽을 수 없는 HttpOnly 쿠키로 주고받는다.
 * - 요청 헤더 X-Auth-Mode: cookie 를 보낸 클라이언트(웹)만 쿠키로 받고, 응답 본문에서는 리프레시 토큰을 뺀다
 *   (앱·테스트처럼 헤더가 없으면 지금처럼 본문으로 준다)
 * - SameSite=Strict: 다른 사이트에서 시작된 요청에는 쿠키가 실리지 않아 CSRF로 토큰을 재발급받을 수 없다
 * - Path=/api/auth: 재발급·로그아웃 요청에만 실린다 (다른 API 요청마다 토큰이 오가지 않게)
 * 웹은 Vercel이 /api를 이 서버로 전달(프록시)해서 같은 사이트로 보이므로, 서드파티 쿠키를 막는 Safari에서도 동작한다.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "fitmate_refresh";
    public static final String MODE_HEADER = "X-Auth-Mode";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration ttl;

    public RefreshTokenCookie(@Value("${fitmate.auth.cookie-secure:true}") boolean secure,
                              @Value("${jwt.refresh-token-ttl}") Duration ttl) {
        this.secure = secure;
        this.ttl = ttl;
    }

    public boolean wanted(HttpServletRequest request) {
        return "cookie".equalsIgnoreCase(request.getHeader(MODE_HEADER));
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? Optional.empty() : Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()) && !cookie.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, ttl).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}

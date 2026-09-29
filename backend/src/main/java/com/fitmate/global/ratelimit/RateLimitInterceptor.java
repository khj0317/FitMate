package com.fitmate.global.ratelimit;

import com.fitmate.global.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @RateLimited가 붙은 API를 호출할 때마다 횟수를 센다. 넘으면 429와 Retry-After 헤더로 응답한다.
 * 컨트롤러 실행 전에 막으므로 사진 업로드 같은 무거운 작업도 시작하지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final RateLimiter rateLimiter;
    private final ClientIp clientIp;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimited limit = method.getMethodAnnotation(RateLimited.class);
        if (limit == null) {
            return true;
        }
        String subject = limit.key() == RateLimited.Key.USER ? userId().orElseGet(() -> ipOf(request)) : ipOf(request);
        try {
            rateLimiter.check(limit.name(), subject, limit.limit(), limit.windowSeconds());
        } catch (RateLimiter.RateLimitExceededException e) {
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            throw e;
        }
        return true;
    }

    private static java.util.Optional<String> userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token
                ? java.util.Optional.of("user:" + token.getName())
                : java.util.Optional.empty();
    }

    private String ipOf(HttpServletRequest request) {
        return "ip:" + clientIp.of(request);
    }
}

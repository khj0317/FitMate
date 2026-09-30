package com.fitmate.global.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청한 사용자의 IP (로그인 시도 제한, IP별 요청 제한에 쓴다).
 * 기본은 server.forward-headers-strategy=native가 신뢰할 수 있는 프록시 헤더로 바꿔 둔 주소다.
 * 웹 요청이 Vercel 프록시를 거치면 그 주소는 Vercel 서버가 되어 모든 사용자가 한 IP로 묶이므로,
 * Vercel 미들웨어(apps/web/middleware.ts)가 사용자 IP를 {@value #CLIENT_IP_HEADER}에 담아 보낸다.
 * 이 헤더는 누구나 서버에 직접 보내 위조할 수 있으므로, 둘만 아는 비밀값({@value #PROXY_SECRET_HEADER})이
 * 맞을 때만 믿는다. 비밀값이 틀리거나 없으면 실제 접속 주소를 쓴다.
 */
@Component
public class ClientIp {

    static final String PROXY_SECRET_HEADER = "x-fitmate-proxy-secret";
    static final String CLIENT_IP_HEADER = "x-fitmate-client-ip";

    private final byte[] proxySecret;

    public ClientIp(@Value("${fitmate.proxy-secret:}") String proxySecret) {
        this.proxySecret = proxySecret == null ? new byte[0] : proxySecret.strip().getBytes(StandardCharsets.UTF_8);
    }

    public String of(HttpServletRequest request) {
        if (fromTrustedProxy(request)) {
            String value = request.getHeader(CLIENT_IP_HEADER);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].strip();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean fromTrustedProxy(HttpServletRequest request) {
        if (proxySecret.length == 0) return false;
        String given = request.getHeader(PROXY_SECRET_HEADER);
        // 한 글자씩 비교하면 응답 시간 차이로 비밀값을 추측할 수 있으므로 일정한 시간에 비교한다
        return given != null && MessageDigest.isEqual(proxySecret, given.getBytes(StandardCharsets.UTF_8));
    }
}

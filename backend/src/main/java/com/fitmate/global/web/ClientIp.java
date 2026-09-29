package com.fitmate.global.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청한 사용자의 IP (로그인 시도 제한, IP별 요청 제한에 쓴다).
 * 기본은 server.forward-headers-strategy=native가 신뢰할 수 있는 프록시 헤더로 바꿔 둔 주소다.
 * 웹 요청이 Vercel 프록시를 거치면 그 주소는 Vercel 서버가 되어 모든 사용자가 한 IP로 묶이므로,
 * fitmate.client-ip-header(예: x-vercel-forwarded-for)를 지정하면 그 헤더의 첫 번째 값을 쓴다.
 * 이 헤더는 서버에 직접 요청하면 위조할 수 있지만, 아이디별 로그인 제한·이메일별 발송 제한은 그대로라
 * IP 제한은 "추가 방어선"으로만 본다. 자체 도메인을 쓰면 api 하위 도메인으로 옮겨 이 설정 없이 운영할 수 있다.
 */
@Component
public class ClientIp {

    private final String header;

    public ClientIp(@Value("${fitmate.client-ip-header:}") String header) {
        this.header = header == null ? "" : header.strip();
    }

    public String of(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.split(",")[0].strip();
            }
        }
        return request.getRemoteAddr();
    }
}

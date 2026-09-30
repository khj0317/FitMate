package com.fitmate.global.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 요청한 사용자의 IP (로그인 시도 제한, IP별 요청 제한에 쓴다). 아래 순서로 정한다.
 * <ol>
 *   <li>Vercel 프록시를 거친 웹 요청: API가 보는 접속 주소는 Vercel 서버라 모든 사용자가 한 IP로 묶이므로,
 *       Vercel 미들웨어(apps/web/middleware.ts)가 사용자 IP를 {@value #CLIENT_IP_HEADER}에 담아 보낸다.
 *       이 헤더는 누구나 위조할 수 있으므로 둘만 아는 비밀값({@value #PROXY_SECRET_HEADER})이 맞을 때만 믿는다.</li>
 *   <li>엣지(CDN)가 넣는 사용자 IP 헤더: Render는 앞단의 Cloudflare가 {@value #CLOUDFLARE_IP_HEADER}를 넣는다.
 *       사용자가 보낸 같은 이름의 헤더는 Cloudflare가 덮어쓰므로 위조할 수 없다. X-Forwarded-For는
 *       "사용자, Cloudflare, Render 내부" 순이라 Tomcat이 공인 IP인 Cloudflare 주소를 사용자로 착각한다
 *       (Cloudflare 서버가 여러 대라 요청마다 IP가 달라져 IP 제한이 동작하지 않았음).</li>
 *   <li>그 밖에는 server.forward-headers-strategy=native가 정한 접속 주소</li>
 * </ol>
 */
@Component
public class ClientIp {

    static final String PROXY_SECRET_HEADER = "x-fitmate-proxy-secret";
    static final String CLIENT_IP_HEADER = "x-fitmate-client-ip";
    static final String CLOUDFLARE_IP_HEADER = "cf-connecting-ip";

    private final byte[] proxySecret;
    private final String edgeIpHeader;

    /**
     * @param edgeIpHeader 엣지가 넣는 사용자 IP 헤더. 비우면 Render(RENDER=true를 자동으로 넣어 줌)에서만 cf-connecting-ip.
     *                     엣지를 거치지 않는 곳에서 켜면 사용자가 헤더를 위조할 수 있으므로 켜지 않는다.
     */
    public ClientIp(
            @Value("${fitmate.proxy-secret:}") String proxySecret,
            @Value("${fitmate.edge-ip-header:}") String edgeIpHeader,
            @Value("${RENDER:false}") boolean onRender) {
        this.proxySecret = proxySecret == null ? new byte[0] : proxySecret.strip().getBytes(StandardCharsets.UTF_8);
        String header = edgeIpHeader == null ? "" : edgeIpHeader.strip();
        this.edgeIpHeader = header.isEmpty() && onRender ? CLOUDFLARE_IP_HEADER : header;
    }

    public String of(HttpServletRequest request) {
        if (fromTrustedProxy(request)) {
            String ip = firstValue(request.getHeader(CLIENT_IP_HEADER));
            if (ip != null) return ip;
        }
        if (!edgeIpHeader.isEmpty()) {
            String ip = firstValue(request.getHeader(edgeIpHeader));
            if (ip != null) return ip;
        }
        return request.getRemoteAddr();
    }

    private boolean fromTrustedProxy(HttpServletRequest request) {
        if (proxySecret.length == 0) return false;
        String given = request.getHeader(PROXY_SECRET_HEADER);
        // 한 글자씩 비교하면 응답 시간 차이로 비밀값을 추측할 수 있으므로 일정한 시간에 비교한다
        return given != null && MessageDigest.isEqual(proxySecret, given.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstValue(String header) {
        if (header == null || header.isBlank()) return null;
        return header.split(",")[0].strip();
    }
}

package com.fitmate.global.ratelimit;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "fitmate.rate-limit.enabled=true")
class RateLimitTest extends IntegrationTest {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    RateLimiter rateLimiter;

    @BeforeEach
    void clearCounters() {
        Set<String> keys = redisTemplate.keys("rate:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("댓글은 5분에 30개까지. 넘으면 429와 Retry-After를 주고, 다른 사람은 영향이 없다")
    void commentLimitPerUser() throws Exception {
        TestUser spammer = signupAndLogin();
        TestUser other = signupAndLogin();
        long postId = createPost(spammer);

        for (int i = 0; i < 30; i++) {
            comment(spammer, postId).andExpect(status().isCreated());
        }
        comment(spammer, postId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        comment(other, postId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("회원가입은 같은 IP에서 1시간에 10번까지")
    void signupLimitPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            signupFrom("10.20.30.40").andExpect(status().isCreated());
        }
        signupFrom("10.20.30.40").andExpect(status().isTooManyRequests());
        signupFrom("10.20.30.41").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비밀값이 맞을 때만(Vercel 미들웨어) 헤더의 사용자 IP를 믿고, 직접 보낸 위조 헤더는 무시한다")
    void clientIpFromTrustedProxyOnly() {
        var clientIp = new com.fitmate.global.web.ClientIp("s3cret", "", false);

        var viaProxy = new org.springframework.mock.web.MockHttpServletRequest();
        viaProxy.setRemoteAddr("76.76.21.21"); // 프록시(Vercel) 주소
        viaProxy.addHeader("x-fitmate-proxy-secret", "s3cret");
        viaProxy.addHeader("x-fitmate-client-ip", "203.0.113.7");
        assertThat(clientIp.of(viaProxy)).isEqualTo("203.0.113.7");

        var forged = new org.springframework.mock.web.MockHttpServletRequest();
        forged.setRemoteAddr("198.51.100.1"); // 서버에 직접 요청하며 IP를 바꿔 제한을 피하려는 경우
        forged.addHeader("x-fitmate-proxy-secret", "guess");
        forged.addHeader("x-fitmate-client-ip", "203.0.113.99");
        assertThat(clientIp.of(forged)).isEqualTo("198.51.100.1");

        var noSecret = new org.springframework.mock.web.MockHttpServletRequest();
        noSecret.setRemoteAddr("198.51.100.1");
        noSecret.addHeader("x-fitmate-client-ip", "203.0.113.99");
        assertThat(clientIp.of(noSecret)).isEqualTo("198.51.100.1");

        // 비밀값을 설정하지 않은 서버(로컬 등)는 헤더를 전혀 믿지 않는다
        assertThat(new com.fitmate.global.web.ClientIp("", "", false).of(viaProxy)).isEqualTo("76.76.21.21");
    }

    @Test
    @DisplayName("Render에서는 Cloudflare가 넣은 cf-connecting-ip를 사용자 IP로 쓴다")
    void cloudflareIpOnRender() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("172.71.195.123"); // Tomcat이 X-Forwarded-For에서 고른 Cloudflare 서버 주소
        request.addHeader("cf-connecting-ip", "81.97.145.24");
        assertThat(new com.fitmate.global.web.ClientIp("", "", true).of(request)).isEqualTo("81.97.145.24");
        // Render가 아니면 이 헤더를 믿지 않는다 (엣지를 거치지 않으면 누구나 보낼 수 있으므로)
        assertThat(new com.fitmate.global.web.ClientIp("", "", false).of(request)).isEqualTo("172.71.195.123");

        // Vercel 미들웨어가 보낸 IP가 우선 (Cloudflare 입장에서는 Vercel 서버가 사용자)
        request.addHeader("x-fitmate-proxy-secret", "s3cret");
        request.addHeader("x-fitmate-client-ip", "203.0.113.7");
        assertThat(new com.fitmate.global.web.ClientIp("s3cret", "", true).of(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("동시에 몰려도 정확히 한도만큼만 통과한다")
    void concurrentRequestsAreCountedExactly() throws Exception {
        List<Boolean> allowed = runConcurrently(30, index -> {
            try {
                rateLimiter.check("test-concurrent", "same-user", 12, 60);
                return true;
            } catch (RateLimiter.RateLimitExceededException e) {
                return false;
            }
        });
        assertThat(allowed).filteredOn(Boolean::booleanValue).hasSize(12);
        assertThat(redisTemplate.getExpire("rate:test-concurrent:same-user")).isBetween(1L, 60L);
    }

    private long createPost(TestUser author) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/posts")
                        .file(new org.springframework.mock.web.MockMultipartFile("post", "", "application/json",
                                "{\"category\": \"FREE\", \"content\": \"도배 테스트\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + author.accessToken()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions comment(TestUser user, long postId) throws Exception {
        return call(HttpMethod.POST, "/api/posts/" + postId + "/comments", "{\"content\": \"도배\"}", user.accessToken());
    }

    private org.springframework.test.web.servlet.ResultActions signupFrom(String ip) throws Exception {
        String suffix = uniqueSuffix();
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/signup")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("r" + suffix, PASSWORD, "rate_" + suffix)));
    }
}

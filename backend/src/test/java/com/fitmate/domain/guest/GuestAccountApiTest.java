package com.fitmate.domain.guest;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestAccountApiTest extends IntegrationTest {

    @Autowired
    GuestCleanupScheduler cleanupScheduler;

    @Test
    @DisplayName("체험 계정은 가입 없이 바로 로그인되고, 성수역 프로필과 데모 사용자와의 채팅·받은 요청이 준비돼 있다")
    void startGuest() throws Exception {
        // 데모 사용자 역할 (배포에는 DemoDataInitializer가 만든 demo02, demo03이 있다)
        post("/api/auth/signup", signupJson("demo02", PASSWORD, "초록고래")).andExpect(status().isCreated());
        post("/api/auth/signup", signupJson("demo03", PASSWORD, "노란병아리")).andExpect(status().isCreated());

        String token = startGuestToken();

        call(HttpMethod.GET, "/api/users/me", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GUEST"))
                .andExpect(jsonPath("$.loginId", startsWith("guest_")))
                .andExpect(jsonPath("$.nickname", startsWith("체험러")))
                .andExpect(jsonPath("$.location.areaName").value("서울 성동구 성수동"))
                .andExpect(jsonPath("$.sports.length()").value(2));
        call(HttpMethod.GET, "/api/chat-rooms", null, token)
                .andExpect(jsonPath("$[0].counterpart.nickname").value("초록고래"))
                .andExpect(jsonPath("$[0].unreadCount").value(2));
        call(HttpMethod.GET, "/api/match-requests/received", null, token)
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("체험 계정은 이메일을 바꾸거나 사진을 올릴 수 없다 (글은 쓸 수 있다)")
    void guestRestrictions() throws Exception {
        String token = startGuestToken();

        call(HttpMethod.PATCH, "/api/users/me", """
                {"email": "someone@fitmate.test", "emailVerificationToken": "x"}
                """, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUEST_RESTRICTED"));
        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(new MockMultipartFile("file", "me.png", "image/png", TestImages.png(100, 100, false)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUEST_RESTRICTED"));
        mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("post", "", "application/json",
                                "{\"category\": \"FREE\", \"content\": \"체험 중이에요\"}".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("만든 지 24시간이 지난 체험 계정은 정리할 때 지워지고, 일반 회원은 남는다")
    void cleanupDeletesExpiredGuests() throws Exception {
        String token = startGuestToken();
        TestUser member = signupAndLogin();

        cleanupScheduler.deleteExpired(Instant.now().minusSeconds(3600)); // 아직 기한 전
        call(HttpMethod.GET, "/api/users/me", null, token).andExpect(status().isOk());

        assertThat(cleanupScheduler.deleteExpired(Instant.now().plusSeconds(1))).isPositive();
        call(HttpMethod.GET, "/api/users/me", null, token).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/users/me", null, member.accessToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("데모 계정은 비밀번호가 공개돼 있으므로 배포 설정(데모 로그인 꺼짐)에서는 맞는 비밀번호로도 로그인할 수 없다")
    void demoAccountLoginLocked() throws Exception {
        post("/api/auth/signup", signupJson("demo77", PASSWORD, "데모칠칠")).andExpect(status().isCreated());

        post("/api/auth/login", loginJson("demo77", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private String startGuestToken() throws Exception {
        String body = post("/api/auth/guest", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}

package com.fitmate.domain.guest;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestAccountApiTest extends IntegrationTest {

    @Autowired
    GuestCleanupScheduler cleanupScheduler;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    @DisplayName("체험 계정은 로그아웃하면 바로 지워지고, 남의 글·모임·채팅에 남긴 흔적도 체험 전으로 돌아간다")
    void logoutDeletesGuestAndRevertsTraces() throws Exception {
        TestUser member = signupAndLogin();
        String postBody = mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("post", "", "application/json",
                                "{\"category\": \"FREE\", \"content\": \"같이 운동해요\"}".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long postId = ((Number) JsonPath.read(postBody, "$.id")).longValue();
        String gatheringBody = call(HttpMethod.POST, "/api/gatherings", String.format(Locale.ROOT, """
                {"sportId": %d, "title": "성수 헬스", "placeName": "성수역", "location": {"latitude": 37.5446, "longitude": 127.0559},
                 "startsAt": "%s", "capacity": 2}
                """, GYM, Instant.now().plus(1, ChronoUnit.DAYS)), member.accessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long gatheringId = ((Number) JsonPath.read(gatheringBody, "$.summary.id")).longValue();

        Guest guest = newGuest();
        String nickname = JsonPath.read(call(HttpMethod.GET, "/api/users/me", null, guest.accessToken())
                .andReturn().getResponse().getContentAsString(), "$.nickname");
        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", null, guest.accessToken()).andExpect(status().is2xxSuccessful());
        call(HttpMethod.POST, "/api/posts/" + postId + "/comments", """
                {"content": "저도 할래요"}
                """, guest.accessToken()).andExpect(status().isCreated());
        call(HttpMethod.POST, "/api/gatherings/" + gatheringId + "/participants", null, guest.accessToken())
                .andExpect(status().isOk());
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, member.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(2))
                .andExpect(jsonPath("$.summary.status").value("CLOSED"));

        post("/api/auth/logout", refreshJson(guest.refreshToken())).andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/users/me", null, guest.accessToken()).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/posts/" + postId, null, member.accessToken())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));
        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, member.accessToken())
                .andExpect(jsonPath("$.length()").value(0));
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, member.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(1))
                .andExpect(jsonPath("$.summary.status").value("RECRUITING"));
        Integer traces = jdbcTemplate.queryForObject("""
                SELECT (SELECT COUNT(*) FROM chat_messages WHERE content LIKE ?)
                     + (SELECT COUNT(*) FROM notifications WHERE title LIKE ? OR body LIKE ?)""",
                Integer.class, nickname + "%", "%" + nickname + "%", "%" + nickname + "%");
        assertThat(traces).isZero();
    }

    @Test
    @DisplayName("체험 계정이 받은 요청을 수락해 생긴 채팅방은 로그아웃하면 상대방 목록에서도 사라진다")
    void logoutRemovesDirectChats() throws Exception {
        TestUser member = signupWithGym();
        Guest guest = newGuest();
        call(HttpMethod.POST, "/api/match-requests", """
                {"receiverId": %d, "sportId": %d, "message": "같이 운동해요"}
                """.formatted(guest.id(), GYM), member.accessToken()).andExpect(status().isCreated());
        String received = call(HttpMethod.GET, "/api/match-requests/received", null, guest.accessToken())
                .andReturn().getResponse().getContentAsString();
        Number requestId = JsonPath.read(received, "$[0].id");
        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, guest.accessToken())
                .andExpect(status().isOk());
        call(HttpMethod.GET, "/api/chat-rooms", null, member.accessToken()).andExpect(jsonPath("$.length()").value(1));

        post("/api/auth/logout", refreshJson(guest.refreshToken())).andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/chat-rooms", null, member.accessToken()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("체험 계정은 다른 사람의 운동 메이트 추천에 나오지 않는다")
    void guestsAreNotRecommended() throws Exception {
        Guest first = newGuest();
        Guest second = newGuest();

        String body = call(HttpMethod.GET, "/api/matching/recommendations?radiusKm=20", null, second.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> userIds = JsonPath.read(body, "$[*].userId");
        assertThat(userIds).extracting(Number::longValue).doesNotContain(first.id());
    }

    @Test
    @DisplayName("일반 회원은 로그아웃해도 계정이 그대로 남는다")
    void memberLogoutKeepsAccount() throws Exception {
        TestUser member = signupAndLogin();

        post("/api/auth/logout", refreshJson(member.refreshToken())).andExpect(status().isNoContent());

        post("/api/auth/login", loginJson(member.loginId(), PASSWORD)).andExpect(status().isOk());
    }

    private String startGuestToken() throws Exception {
        return newGuest().accessToken();
    }

    private Guest newGuest() throws Exception {
        String body = post("/api/auth/guest", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String me = call(HttpMethod.GET, "/api/users/me", null, accessToken).andReturn().getResponse().getContentAsString();
        return new Guest(((Number) JsonPath.read(me, "$.id")).longValue(), accessToken, JsonPath.read(body, "$.refreshToken"));
    }

    private record Guest(Long id, String accessToken, String refreshToken) {
    }
}

package com.fitmate.domain.admin;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("관리자가 아니면 관리자 API를 쓸 수 없고, 내 프로필에 권한이 표시된다")
    void onlyAdmins() throws Exception {
        TestUser user = signupAndLogin();
        call(HttpMethod.GET, "/api/admin/stats", null, user.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.role").value("USER"));

        TestUser admin = admin();
        call(HttpMethod.GET, "/api/admin/stats", null, admin.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());
        call(HttpMethod.GET, "/api/users/me", null, admin.accessToken())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("정지하면 같은 사람의 대기 신고가 모두 처리되고, 로그인·토큰 재발급이 막히며 신고자에게 알림이 간다")
    void suspendResolvesAllPendingReports() throws Exception {
        TestUser admin = admin();
        TestUser target = signupAndLogin();
        TestUser reporter1 = signupAndLogin();
        TestUser reporter2 = signupAndLogin();
        long reportId = report(reporter1, target);
        report(reporter2, target);

        call(HttpMethod.GET, "/api/admin/reports", null, admin.accessToken())
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) reportId)))
                .andExpect(jsonPath("$.items[?(@.id == %d)].reported.totalReports".formatted(reportId)).value(hasItem(2)));

        call(HttpMethod.POST, "/api/admin/reports/" + reportId + "/resolve", """
                {"action": "SUSPEND_7D", "note": "욕설 반복"}
                """, admin.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handledReports").value(2));

        // 다시 처리하면 이미 처리됨
        call(HttpMethod.POST, "/api/admin/reports/" + reportId + "/resolve", """
                {"action": "WARN"}
                """, admin.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_HANDLED"));

        post("/api/auth/login", loginJson(target.loginId(), PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        post("/api/auth/refresh", refreshJson(target.refreshToken()))
                .andExpect(status().isUnauthorized());

        for (TestUser reporter : List.of(reporter1, reporter2)) {
            call(HttpMethod.GET, "/api/notifications", null, reporter.accessToken())
                    .andExpect(jsonPath("$.items[0].type").value("REPORT_RESOLVED"));
        }
        call(HttpMethod.GET, "/api/admin/reports?pending=false", null, admin.accessToken())
                .andExpect(jsonPath("$.items[?(@.id == %d)].action".formatted(reportId)).value(hasItem("SUSPEND_7D")));

        call(HttpMethod.POST, "/api/admin/users/" + target.id() + "/unsuspend", null, admin.accessToken())
                .andExpect(status().isNoContent());
        post("/api/auth/login", loginJson(target.loginId(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("경고하면 신고당한 사람에게 끌 수 없는 운영 알림이 간다")
    void warnSendsSystemNotification() throws Exception {
        TestUser admin = admin();
        TestUser target = signupAndLogin();
        TestUser reporter = signupAndLogin();
        call(HttpMethod.PUT, "/api/notifications/settings", """
                {"muted": ["MATCH", "GATHERING", "MANNER", "COMMUNITY"]}
                """, target.accessToken()).andExpect(status().isOk());
        long reportId = report(reporter, target);

        call(HttpMethod.POST, "/api/admin/reports/" + reportId + "/resolve", """
                {"action": "WARN"}
                """, admin.accessToken()).andExpect(status().isOk());

        call(HttpMethod.GET, "/api/notifications", null, target.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("ADMIN_WARNING"));
        post("/api/auth/login", loginJson(target.loginId(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자가 숨긴 글은 작성자에게도 보이지 않고, 숨김을 풀면 다시 보인다")
    void hidePost() throws Exception {
        TestUser admin = admin();
        TestUser author = signupAndLogin();
        String body = mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("post", "", "application/json",
                                "{\"category\": \"FREE\", \"content\": \"hide me\"}".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + author.accessToken()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long postId = ((Number) JsonPath.read(body, "$.id")).longValue();

        call(HttpMethod.POST, "/api/admin/posts/" + postId + "/hide", null, author.accessToken())
                .andExpect(status().isForbidden());
        call(HttpMethod.POST, "/api/admin/posts/" + postId + "/hide", null, admin.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/posts/" + postId, null, author.accessToken())
                .andExpect(status().isNotFound());

        call(HttpMethod.POST, "/api/admin/posts/" + postId + "/unhide", null, admin.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/posts/" + postId, null, author.accessToken())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 계정은 정지할 수 없다")
    void cannotSuspendAdmin() throws Exception {
        TestUser admin = admin();
        TestUser otherAdmin = admin();
        long reportId = report(signupAndLogin(), otherAdmin);
        call(HttpMethod.POST, "/api/admin/reports/" + reportId + "/resolve", """
                {"action": "SUSPEND_PERMANENT"}
                """, admin.accessToken())
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM user_reports WHERE id = ?", String.class, reportId))
                .isEqualTo("PENDING"); // 실패하면 처리 기록도 롤백된다
    }

    private TestUser admin() throws Exception {
        TestUser user = signupAndLogin();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.id());
        return user;
    }

    private long report(TestUser reporter, TestUser target) throws Exception {
        call(HttpMethod.POST, "/api/users/" + target.id() + "/report", """
                {"reason": "ABUSE", "detail": "채팅에서 욕설", "block": false}
                """, reporter.accessToken()).andExpect(status().is2xxSuccessful());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM user_reports WHERE reporter_id = ? AND reported_id = ? AND status = 'PENDING'",
                Long.class, reporter.id(), target.id());
    }
}

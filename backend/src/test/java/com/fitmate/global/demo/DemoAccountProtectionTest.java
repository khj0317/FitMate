package com.fitmate.global.demo;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 배포 환경처럼 데모 데이터를 켠 상태 */
@TestPropertySource(properties = "fitmate.demo-data.enabled=true")
class DemoAccountProtectionTest extends IntegrationTest {

    @Test
    @DisplayName("데모 데이터를 켜면 체험 계정으로 로그인할 수 있고, 체험 계정은 탈퇴할 수 없다")
    void demoAccountCannotWithdraw() throws Exception {
        String body = post("/api/auth/login", loginJson("demo01", "password123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        call(HttpMethod.POST, "/api/users/me/withdrawal", """
                {"password": "password123"}
                """, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_PROTECTED"));
        call(HttpMethod.GET, "/api/gatherings/mine", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("일반 계정은 데모 데이터를 켜도 그대로 탈퇴할 수 있다")
    void normalAccountCanWithdraw() throws Exception {
        TestUser user = signupAndLogin();
        call(HttpMethod.POST, "/api/users/me/withdrawal", """
                {"password": "%s"}
                """.formatted(PASSWORD), user.accessToken())
                .andExpect(status().isNoContent());
    }
}

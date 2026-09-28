package com.fitmate.domain.auth;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTest {

    @Test
    @DisplayName("회원가입 → 로그인 → 가입할 때 입력한 정보가 내 프로필에 들어가 있다")
    void signupLoginAndGetMe() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(user.loginId()))
                .andExpect(jsonPath("$.nickname").value(user.nickname()))
                .andExpect(jsonPath("$.email").value(user.loginId() + "@fitmate.test"))
                .andExpect(jsonPath("$.birthDate").value("1998-05-20"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.location.areaName").value("테스트 지역"))
                .andExpect(jsonPath("$.mannerScore").value(36.5))
                .andExpect(jsonPath("$.searchRadiusKm").value(5));
    }

    @Test
    @DisplayName("이메일은 필수이고, 소문자로 저장된다")
    void requiredEmail() throws Exception {
        String suffix = uniqueSuffix();
        String loginId = "mail" + suffix;
        post("/api/auth/signup", signupJson(loginId, PASSWORD, PASSWORD, "mail_" + suffix, "Mail_" + suffix + "@FitMate.com"))
                .andExpect(status().isCreated());

        String token = JsonPath.read(post("/api/auth/login", loginJson(loginId, PASSWORD))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        call(HttpMethod.GET, "/api/users/me", null, token)
                .andExpect(jsonPath("$.email").value("mail_" + suffix + "@fitmate.com"));

        post("/api/auth/signup", signupJson("nomail" + suffix, PASSWORD, PASSWORD, "nomail_" + suffix, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    @Test
    @DisplayName("아이디는 앞뒤 공백을 지우고 소문자로 저장·비교한다")
    void loginIdIsNormalized() throws Exception {
        String suffix = uniqueSuffix();
        post("/api/auth/signup", signupJson("  Mixed_" + suffix + " ", PASSWORD, PASSWORD, "mixed_" + suffix, "mixed_" + suffix + "@fitmate.test"))
                .andExpect(status().isCreated());

        post("/api/auth/login", loginJson("MIXED_" + suffix, PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미 사용 중인 아이디, 이메일, 닉네임이면 409")
    void duplicateSignup() throws Exception {
        TestUser user = signupAndLogin();
        String email = "dup_" + uniqueSuffix() + "@fitmate.com";
        post("/api/auth/signup", signupJson("e" + uniqueSuffix(), PASSWORD, PASSWORD, "e_" + uniqueSuffix(), email))
                .andExpect(status().isCreated());

        post("/api/auth/signup", signupJson(user.loginId(), PASSWORD, "other_" + uniqueSuffix()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));

        post("/api/auth/signup", signupJson("o" + uniqueSuffix(), PASSWORD, PASSWORD, "other_" + uniqueSuffix(), email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        post("/api/auth/signup", signupJson("o" + uniqueSuffix(), PASSWORD, user.nickname()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("같은 아이디로 동시에 가입해도 한 명만 성공하고 나머지는 409")
    void concurrentSignupWithSameLoginId() throws Exception {
        String loginId = "race" + uniqueSuffix();
        int threads = 10;

        List<Integer> statuses = runConcurrently(threads, i ->
                post("/api/auth/signup", signupJson(loginId, PASSWORD, "race_" + uniqueSuffix()))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
    }

    @Test
    @DisplayName("회원가입 입력값이 잘못되거나 빠지면 400과 필드별 에러를 준다")
    void signupValidation() throws Exception {
        post("/api/auth/signup", """
                {"loginId": "ab", "password": "short", "passwordConfirm": "short", "nickname": "!", "email": "not-an-email"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("loginId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("nickname")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("birthDate")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("gender")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("location")));
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 PASSWORD_MISMATCH")
    void passwordMismatch() throws Exception {
        String suffix = uniqueSuffix();
        post("/api/auth/signup", signupJson("p" + suffix, PASSWORD, "different123", "p_" + suffix, "p" + suffix + "@fitmate.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("비밀번호가 틀리거나 없는 계정이면 똑같이 401 INVALID_CREDENTIALS")
    void loginFailure() throws Exception {
        TestUser user = signupAndLogin();

        post("/api/auth/login", loginJson(user.loginId(), "wrongPassword1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        post("/api/auth/login", loginJson("nobody" + uniqueSuffix(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("토큰이 없으면 UNAUTHORIZED, 잘못된 토큰이면 INVALID_TOKEN")
    void protectedApiWithoutValidToken() throws Exception {
        call(HttpMethod.GET, "/api/users/me", null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        call(HttpMethod.GET, "/api/users/me", null, "invalid.token.value")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("리프레시 토큰은 한 번만 쓸 수 있고, 재발급하면 새 토큰으로 교체된다")
    void refreshTokenRotation() throws Exception {
        TestUser user = signupAndLogin();

        String body = post("/api/auth/refresh", refreshJson(user.refreshToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(body, "$.accessToken");
        String newRefreshToken = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(user.refreshToken());

        // 이미 사용한 토큰 재사용 불가
        post("/api/auth/refresh", refreshJson(user.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 새 토큰은 정상 동작
        call(HttpMethod.GET, "/api/users/me", null, newAccessToken).andExpect(status().isOk());
        post("/api/auth/refresh", refreshJson(newRefreshToken)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 리프레시 토큰으로 동시에 재발급을 요청해도 한 번만 성공한다")
    void concurrentRefresh() throws Exception {
        TestUser user = signupAndLogin();

        List<Integer> statuses = runConcurrently(5, i ->
                post("/api/auth/refresh", refreshJson(user.refreshToken()))
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 401).hasSize(4);
    }

    @Test
    @DisplayName("로그아웃하면 리프레시 토큰을 더 이상 쓸 수 없다")
    void logoutRevokesRefreshToken() throws Exception {
        TestUser user = signupAndLogin();

        post("/api/auth/logout", refreshJson(user.refreshToken())).andExpect(status().isNoContent());

        post("/api/auth/refresh", refreshJson(user.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}

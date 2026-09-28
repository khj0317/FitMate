package com.fitmate.domain.account;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.RecordingAccountMailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountRecoveryApiTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "newPassword456";

    @Autowired
    private RecordingAccountMailSender mailbox;

    @Test
    @DisplayName("아이디 찾기: 등록한 이메일로 아이디를 보낸다")
    void findLoginId() throws Exception {
        Account account = signupWithEmail();

        post("/api/auth/find-login-id", emailJson(account.email())).andExpect(status().isAccepted());

        assertThat(mailbox.loginIdSentTo(account.email())).contains(account.loginId());
    }

    @Test
    @DisplayName("아이디 찾기: 가입되지 않은 이메일도 똑같이 202를 주고 메일은 보내지 않는다 (가입 여부 비노출)")
    void findLoginIdWithUnknownEmail() throws Exception {
        String unknown = "nobody_" + uniqueSuffix() + "@fitmate.com";

        post("/api/auth/find-login-id", emailJson(unknown)).andExpect(status().isAccepted());

        assertThat(mailbox.loginIdSentTo(unknown)).isEmpty();
    }

    @Test
    @DisplayName("같은 대상에게 1분 안에 다시 요청하면 429")
    void cooldown() throws Exception {
        Account account = signupWithEmail();

        post("/api/auth/find-login-id", emailJson(account.email())).andExpect(status().isAccepted());
        post("/api/auth/find-login-id", emailJson(account.email()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("비밀번호 재설정: 코드 요청 → 재설정 → 새 비밀번호로만 로그인되고 기존 로그인은 모두 끊긴다")
    void resetPassword() throws Exception {
        Account account = signupWithEmail();
        String oldRefreshToken = login(account.loginId(), PASSWORD);

        post("/api/auth/password-reset/request", resetRequestJson(account.loginId(), account.email()))
                .andExpect(status().isAccepted());
        String code = mailbox.resetCodeSentTo(account.email()).orElseThrow();
        assertThat(code).matches("\\d{6}");

        post("/api/auth/password-reset/confirm", confirmJson(account.loginId(), code))
                .andExpect(status().isNoContent());

        post("/api/auth/login", loginJson(account.loginId(), PASSWORD)).andExpect(status().isUnauthorized());
        post("/api/auth/login", loginJson(account.loginId(), NEW_PASSWORD)).andExpect(status().isOk());
        post("/api/auth/refresh", refreshJson(oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 한 번 쓴 코드는 다시 쓸 수 없다
        post("/api/auth/password-reset/confirm", confirmJson(account.loginId(), code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    @DisplayName("아이디와 이메일이 일치하지 않으면 코드를 보내지 않는다 (응답은 똑같이 202)")
    void resetRequestWithWrongEmail() throws Exception {
        Account account = signupWithEmail();
        String otherEmail = "other_" + uniqueSuffix() + "@fitmate.com";

        post("/api/auth/password-reset/request", resetRequestJson(account.loginId(), otherEmail))
                .andExpect(status().isAccepted());

        assertThat(mailbox.resetCodeSentTo(otherEmail)).isEmpty();
        assertThat(mailbox.resetCodeSentTo(account.email())).isEmpty();
    }

    @Test
    @DisplayName("인증 코드를 5번 틀리면 코드가 폐기돼 맞는 코드도 쓸 수 없다 (무차별 대입 방지)")
    void tooManyWrongCodes() throws Exception {
        Account account = signupWithEmail();
        post("/api/auth/password-reset/request", resetRequestJson(account.loginId(), account.email()));
        String code = mailbox.resetCodeSentTo(account.email()).orElseThrow();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < PasswordResetCodeStore.MAX_ATTEMPTS; i++) {
            post("/api/auth/password-reset/confirm", confirmJson(account.loginId(), wrong))
                    .andExpect(status().isBadRequest());
        }

        post("/api/auth/password-reset/confirm", confirmJson(account.loginId(), code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        post("/api/auth/login", loginJson(account.loginId(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호 확인이 다르거나 형식이 틀리면 400")
    void invalidNewPassword() throws Exception {
        String loginId = "x" + uniqueSuffix();

        post("/api/auth/password-reset/confirm", """
                {"loginId": "%s", "code": "123456", "newPassword": "newPassword456", "newPasswordConfirm": "different789"}
                """.formatted(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        post("/api/auth/password-reset/confirm", """
                {"loginId": "%s", "code": "12", "newPassword": "short", "newPasswordConfirm": "short"}
                """.formatted(loginId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ---------- helpers ----------

    private record Account(String loginId, String email) {
    }

    private Account signupWithEmail() throws Exception {
        String suffix = uniqueSuffix();
        String loginId = "r" + suffix;
        String email = "recover_" + suffix + "@fitmate.com";
        post("/api/auth/signup", signupJson(loginId, PASSWORD, PASSWORD, "recover_" + suffix, email))
                .andExpect(status().isCreated());
        return new Account(loginId, email);
    }

    private String login(String loginId, String password) throws Exception {
        String body = post("/api/auth/login", loginJson(loginId, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private static String emailJson(String email) {
        return "{\"email\": \"%s\"}".formatted(email);
    }

    private static String resetRequestJson(String loginId, String email) {
        return "{\"loginId\": \"%s\", \"email\": \"%s\"}".formatted(loginId, email);
    }

    private static String confirmJson(String loginId, String code) {
        return """
                {"loginId": "%s", "code": "%s", "newPassword": "%s", "newPasswordConfirm": "%s"}
                """.formatted(loginId, code, NEW_PASSWORD, NEW_PASSWORD);
    }
}

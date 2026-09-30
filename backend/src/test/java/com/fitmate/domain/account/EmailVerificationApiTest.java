package com.fitmate.domain.account;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.RecordingAccountMailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "fitmate.email-verification.enabled=true")
class EmailVerificationApiTest extends IntegrationTest {

    @Autowired
    RecordingAccountMailSender mailSender;

    @Test
    @DisplayName("이메일 인증 없이는 가입할 수 없고, 코드를 확인해 받은 토큰으로 가입할 수 있다")
    void signupRequiresVerifiedEmail() throws Exception {
        String suffix = uniqueSuffix();
        String email = "v" + suffix + "@fitmate.test";

        post("/api/auth/signup", signupJson("v" + suffix, PASSWORD, "verify_" + suffix))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = verify(email);
        post("/api/auth/signup", withToken(signupJson("v" + suffix, PASSWORD, "verify_" + suffix), token))
                .andExpect(status().isCreated());
        post("/api/auth/login", loginJson("v" + suffix, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 이메일로 받은 토큰, 틀린 코드, 이미 쓴 코드로는 인증할 수 없다")
    void rejectsWrongCodeAndToken() throws Exception {
        String suffix = uniqueSuffix();
        String email = "w" + suffix + "@fitmate.test";
        String otherToken = verify("other" + suffix + "@fitmate.test");

        post("/api/auth/signup", withToken(signupJson("w" + suffix, PASSWORD, "wrong_" + suffix), otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        post("/api/auth/email-verification/request", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isAccepted());
        String code = mailSender.signupCodeSentTo(email).orElseThrow();
        String wrong = code.equals("000000") ? "111111" : "000000";
        confirm(email, wrong).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        confirm(email, code).andExpect(status().isOk());
        confirm(email, code).andExpect(status().isBadRequest()); // 한 번 쓴 코드는 사라진다
    }

    @Test
    @DisplayName("이미 가입된 이메일에는 코드를 보내지 않고, 같은 이메일은 1분에 한 번만 보낸다")
    void duplicateAndCooldown() throws Exception {
        String suffix = uniqueSuffix();
        String email = "c" + suffix + "@fitmate.test";
        String token = verify(email);
        post("/api/auth/signup", withToken(signupJson("c" + suffix, PASSWORD, "cool_" + suffix), token))
                .andExpect(status().isCreated());

        post("/api/auth/email-verification/request", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        String fresh = "fresh" + suffix + "@fitmate.test";
        post("/api/auth/email-verification/request", "{\"email\": \"%s\"}".formatted(fresh))
                .andExpect(status().isAccepted());
        post("/api/auth/email-verification/request", "{\"email\": \"%s\"}".formatted(fresh))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("프로필에서 이메일을 바꿀 때도 새 이메일 인증이 필요하다 (그대로 두면 필요 없음)")
    void changingEmailRequiresVerification() throws Exception {
        String suffix = uniqueSuffix();
        String loginId = "p" + suffix;
        String email = loginId + "@fitmate.test";
        post("/api/auth/signup", withToken(signupJson(loginId, PASSWORD, "prof_" + suffix), verify(email)))
                .andExpect(status().isCreated());
        String body = post("/api/auth/login", loginJson(loginId, PASSWORD)).andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\"}".formatted(email), accessToken)
                .andExpect(status().isOk());
        String newEmail = "new" + suffix + "@fitmate.test";
        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\"}".formatted(newEmail), accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // 다른 항목(생일) 때문에 저장이 실패해도 1회용 인증 토큰은 남아 있어서, 고쳐서 다시 저장할 수 있다
        String token = verify(newEmail);
        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\", \"emailVerificationToken\": \"%s\", \"birthDate\": \"1800-01-01\"}"
                .formatted(newEmail, token), accessToken)
                .andExpect(status().isBadRequest());
        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\", \"emailVerificationToken\": \"%s\"}"
                .formatted(newEmail, token), accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    private String verify(String email) throws Exception {
        post("/api/auth/email-verification/request", "{\"email\": \"%s\"}".formatted(email))
                .andExpect(status().isAccepted());
        String code = mailSender.signupCodeSentTo(email).orElseThrow();
        assertThat(code).hasSize(6);
        String body = confirm(email, code).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.verificationToken");
    }

    private ResultActions confirm(String email, String code) throws Exception {
        return post("/api/auth/email-verification/confirm", """
                {"email": "%s", "code": "%s"}
                """.formatted(email, code));
    }

    private static String withToken(String signupJson, String token) {
        return signupJson.replaceFirst("\\{", "{\"emailVerificationToken\": \"" + token + "\", ");
    }
}

package com.fitmate.domain.auth;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 다른 테스트의 로그인과 IP 기록이 섞이지 않게 테스트마다 임의의 IP로 요청한다. */
class LoginAttemptLimitTest extends IntegrationTest {

    @Test
    @DisplayName("같은 아이디로 5번 틀리면 잠기고, 잠긴 동안은 맞는 비밀번호로도 로그인할 수 없다")
    void lockAfterFiveFailures() throws Exception {
        TestUser user = signupAndLogin();
        String ip = randomIp();

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_LOGIN_ID; i++) {
            login(user.loginId(), "wrongPassword1", ip).andExpect(status().isUnauthorized());
        }

        login(user.loginId(), PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.message", containsString("15분 뒤에")));
        // 다른 IP에서 시도해도 아이디 기준으로 잠겨 있다
        login(user.loginId(), PASSWORD, randomIp()).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("잠기기 전에 로그인에 성공하면 실패 횟수가 초기화된다")
    void successResetsFailures() throws Exception {
        TestUser user = signupAndLogin();
        String ip = randomIp();

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_LOGIN_ID - 1; i++) {
            login(user.loginId(), "wrongPassword1", ip);
        }
        login(user.loginId(), PASSWORD, ip).andExpect(status().isOk());

        login(user.loginId(), "wrongPassword1", ip).andExpect(status().isUnauthorized());
        login(user.loginId(), PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    @DisplayName("아이디를 바꿔 가며 시도해도 같은 IP에서 20번 틀리면 그 IP는 잠긴다")
    void lockByIp() throws Exception {
        TestUser user = signupAndLogin();
        String ip = randomIp();

        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_IP; i++) {
            login("attacker" + i + uniqueSuffix(), "wrongPassword1", ip).andExpect(status().isUnauthorized());
        }

        login(user.loginId(), PASSWORD, ip).andExpect(status().isTooManyRequests());
        login(user.loginId(), PASSWORD, randomIp()).andExpect(status().isOk());
    }

    private ResultActions login(String loginId, String password, String ip) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(loginId, password))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }
}

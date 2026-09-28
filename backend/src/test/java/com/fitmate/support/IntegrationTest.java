package com.fitmate.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨테이너와 스프링 컨텍스트를 모든 테스트 클래스가 공유하므로,
 * 테스트끼리 데이터가 겹치지 않게 매번 고유한 이메일/닉네임을 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    protected ResultActions call(HttpMethod method, String url, String json, String accessToken) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url);
        if (json != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return mockMvc.perform(builder);
    }

    protected ResultActions post(String url, String json) throws Exception {
        return call(HttpMethod.POST, url, json, null);
    }

    protected TestUser signupAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "user_" + suffix + "@fitmate.com";
        String nickname = "user_" + suffix;

        post("/api/auth/signup", signupJson(email, PASSWORD, nickname)).andExpect(status().isCreated());
        String body = post("/api/auth/login", loginJson(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new TestUser(email, nickname,
                JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }

    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String signupJson(String email, String password, String nickname) {
        return """
                {"email": "%s", "password": "%s", "nickname": "%s"}
                """.formatted(email, password, nickname);
    }

    protected static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    protected static String refreshJson(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    protected record TestUser(String email, String nickname, String accessToken, String refreshToken) {
    }
}

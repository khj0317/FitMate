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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    protected static final int GYM = 1;

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

        String signup = post("/api/auth/signup", signupJson(email, PASSWORD, nickname))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String body = post("/api/auth/login", loginJson(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new TestUser(
                ((Number) JsonPath.read(signup, "$.userId")).longValue(),
                email, nickname,
                JsonPath.read(body, "$.accessToken"),
                JsonPath.read(body, "$.refreshToken"));
    }

    /** 헬스를 등록한 사용자를 만든다 (매칭 요청을 받으려면 해당 종목이 등록돼 있어야 함) */
    protected TestUser signupWithGym() throws Exception {
        TestUser user = signupAndLogin();
        call(HttpMethod.PUT, "/api/users/me/sports", """
                {"sports": [{"sportId": %d, "skillLevel": "BEGINNER"}]}
                """.formatted(GYM), user.accessToken())
                .andExpect(status().isOk());
        return user;
    }

    protected ResultActions requestMatch(TestUser from, TestUser to) throws Exception {
        return call(HttpMethod.POST, "/api/match-requests", """
                {"receiverId": %d, "sportId": %d, "message": "같이 운동해요"}
                """.formatted(to.id(), GYM), from.accessToken());
    }

    protected Long createdMatchRequestId(TestUser from, TestUser to) throws Exception {
        String body = requestMatch(from, to).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.matchRequestId")).longValue();
    }

    /** a가 b에게 요청하고 b가 수락해서 만들어진 채팅방 ID */
    protected Long matchedRoomId(TestUser a, TestUser b) throws Exception {
        Long requestId = createdMatchRequestId(a, b);
        String body = call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, b.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.chatRoomId")).longValue();
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

    protected interface IndexedTask<T> {
        T run(int index) throws Exception;
    }

    /** 모든 스레드가 준비된 뒤 한꺼번에 출발시켜 실제 경쟁 상황을 만든다. */
    protected static <T> List<T> runConcurrently(int threads, IndexedTask<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<T> callable = () -> {
                    ready.countDown();
                    start.await();
                    return task.run(index);
                };
                futures.add(executor.submit(callable));
            }
            ready.await();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    protected record TestUser(Long id, String email, String nickname, String accessToken, String refreshToken) {
    }
}

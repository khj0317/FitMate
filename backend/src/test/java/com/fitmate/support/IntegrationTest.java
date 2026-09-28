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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

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
        String suffix = uniqueSuffix();
        String loginId = "u" + suffix;
        String nickname = "user_" + suffix;

        String signup = post("/api/auth/signup", signupJson(loginId, PASSWORD, nickname))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String body = post("/api/auth/login", loginJson(loginId, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new TestUser(
                ((Number) JsonPath.read(signup, "$.userId")).longValue(),
                loginId, nickname,
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

    protected static String signupJson(String loginId, String password, String nickname) {
        return signupJson(loginId, password, password, nickname, null);
    }

    /**
     * 활동 지역은 매번 지구 위 임의의 지점으로 넣어서, 매칭 테스트가 다른 테스트의 사용자와 섞이지 않게 한다.
     * email이 null이면 필드를 보내지 않는다 (선택 입력).
     */
    protected static String signupJson(String loginId, String password, String passwordConfirm, String nickname, String email) {
        double latitude = ThreadLocalRandom.current().nextDouble(-60, 60);
        double longitude = ThreadLocalRandom.current().nextDouble(-170, 170);
        return String.format(Locale.ROOT, """
                {"loginId": "%s", "password": "%s", "passwordConfirm": "%s", "nickname": "%s", %s
                 "birthDate": "1998-05-20", "gender": "MALE",
                 "location": {"latitude": %.6f, "longitude": %.6f, "areaName": "테스트 지역"}}
                """, loginId, password, passwordConfirm, nickname,
                email == null ? "" : "\"email\": \"" + email + "\",", latitude, longitude);
    }

    protected static String loginJson(String loginId, String password) {
        return """
                {"loginId": "%s", "password": "%s"}
                """.formatted(loginId, password);
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

    protected record TestUser(Long id, String loginId, String nickname, String accessToken, String refreshToken) {
    }
}

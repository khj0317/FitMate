package com.fitmate.domain.chat;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 실제 포트로 서버를 띄우고 STOMP 클라이언트로 접속해서
 * 인증 → 구독 → 전송 → (DB 저장 → Redis 발행 → 구독) → 수신 전체 흐름을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketTest extends IntegrationTest {

    /** SUBSCRIBE 처리는 비동기라 구독이 등록될 때까지 잠시 기다린다 */
    private static final long SUBSCRIBE_SETTLE_MILLIS = 500;

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    @DisplayName("A가 STOMP로 보낸 메시지를 같은 방을 구독한 B가 실시간으로 받고, DB에도 저장된다")
    void sendAndReceive() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        StompSession sessionA = connect(a);
        StompSession sessionB = connect(b);
        BlockingQueue<String> received = subscribe(sessionB, "/topic/chat-rooms/" + roomId);
        Thread.sleep(SUBSCRIBE_SETTLE_MILLIS);

        sendJson(sessionA, "/app/chat-rooms/" + roomId + "/messages", "{\"content\": \"실시간 안녕!\"}");

        String message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat((String) JsonPath.read(message, "$.content")).isEqualTo("실시간 안녕!");
        assertThat((String) JsonPath.read(message, "$.senderNickname")).isEqualTo(a.nickname());
        assertThat(((Number) JsonPath.read(message, "$.roomId")).longValue()).isEqualTo(roomId);

        call(HttpMethod.GET, "/api/chat-rooms", null, b.accessToken())
                .andExpect(jsonPath("$[0].lastMessage.content").value("실시간 안녕!"))
                .andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    @Test
    @DisplayName("REST로 보낸 메시지도 WebSocket 구독자에게 전달된다")
    void restMessageIsBroadcast() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        BlockingQueue<String> received = subscribe(connect(b), "/topic/chat-rooms/" + roomId);
        Thread.sleep(SUBSCRIBE_SETTLE_MILLIS);

        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "REST에서 보냄"}
                """, a.accessToken());

        String message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat((String) JsonPath.read(message, "$.content")).isEqualTo("REST에서 보냄");
    }

    @Test
    @DisplayName("토큰 없이 또는 잘못된 토큰으로는 연결할 수 없다")
    void connectWithoutValidToken() {
        assertThatThrownBy(() -> connectWithToken(null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connectWithToken("invalid.token.value")).isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("참여하지 않은 채팅방을 구독하면 서버가 연결을 끊는다")
    void strangerCannotSubscribe() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        TestUser stranger = signupAndLogin();
        Long roomId = matchedRoomId(a, b);

        StompSession session = connect(stranger);
        BlockingQueue<String> received = subscribe(session, "/topic/chat-rooms/" + roomId);

        assertThat(waitUntilDisconnected(session)).isTrue();
        assertThat(received).isEmpty();
    }

    private StompSession connect(TestUser user) throws Exception {
        return connectWithToken(user.accessToken());
    }

    private StompSession connectWithToken(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add("Authorization", "Bearer " + accessToken);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private static BlockingQueue<String> subscribe(StompSession session, String destination) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return queue;
    }

    private static void sendJson(StompSession session, String destination, String json) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.set("content-type", "application/json");
        session.send(headers, json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean waitUntilDisconnected(StompSession session) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!session.isConnected()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}

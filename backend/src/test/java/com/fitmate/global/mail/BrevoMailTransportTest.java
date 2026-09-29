package com.fitmate.global.mail;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 가짜 Brevo 서버를 띄워 요청 형식(API 키 헤더, 발신자, 받는 사람, 내용)을 확인한다 */
class BrevoMailTransportTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private volatile int status = 201;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"messageId\":\"<test@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("API 키 헤더와 함께 발신자·받는 사람·제목·HTML을 JSON으로 보낸다")
    void sendsExpectedRequest() {
        transport().send("user@example.com", "[FitMate] 이메일 인증 코드", "<p>123456</p>");

        assertThat(apiKey.get()).isEqualTo("test-key");
        String json = body.get();
        assertThat((String) JsonPath.read(json, "$.sender.email")).isEqualTo("me@gmail.com");
        assertThat((String) JsonPath.read(json, "$.sender.name")).isEqualTo("FitMate");
        assertThat((String) JsonPath.read(json, "$.to[0].email")).isEqualTo("user@example.com");
        assertThat((String) JsonPath.read(json, "$.subject")).isEqualTo("[FitMate] 이메일 인증 코드");
        assertThat((String) JsonPath.read(json, "$.htmlContent")).isEqualTo("<p>123456</p>");
    }

    @Test
    @DisplayName("Brevo가 에러를 주면 예외를 던진다 (호출한 쪽에서 로그를 남김)")
    void throwsOnError() {
        status = 401;
        assertThatThrownBy(() -> transport().send("user@example.com", "제목", "<p>본문</p>"))
                .isInstanceOf(RuntimeException.class);
    }

    private BrevoMailTransport transport() {
        return new BrevoMailTransport("test-key", "http://127.0.0.1:" + server.getAddress().getPort(),
                "FitMate <me@gmail.com>");
    }
}

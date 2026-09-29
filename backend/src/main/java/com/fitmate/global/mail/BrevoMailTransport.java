package com.fitmate.global.mail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Brevo(무료 하루 300통) HTTP API로 보낸다. 도메인 없이도 "발신자 인증"한 메일 주소(예: 내 Gmail)로 보낼 수 있다.
 * 발신자는 app.mail.from("FitMate &lt;me@gmail.com&gt;")을 쓰고, 이 주소는 Brevo에서 인증해 둬야 한다.
 */
@Component
@ConditionalOnExpression("'${fitmate.mail.brevo-api-key:}' != ''")
public class BrevoMailTransport implements MailTransport {

    private final RestClient restClient;
    private final Map<String, String> sender;

    public BrevoMailTransport(@Value("${fitmate.mail.brevo-api-key}") String apiKey,
                              @Value("${fitmate.mail.brevo-base-url:https://api.brevo.com}") String baseUrl,
                              @Value("${app.mail.from}") String from) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .build();
        this.sender = parseSender(from);
    }

    @Override
    public void send(String to, String subject, String html) {
        restClient.post()
                .uri("/v3/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", sender,
                        "to", List.of(Map.of("email", to)),
                        "subject", subject,
                        "htmlContent", html))
                .retrieve()
                .toBodilessEntity(); // 4xx·5xx면 예외 → 호출한 쪽에서 로그를 남긴다
    }

    /** "FitMate <me@gmail.com>" → {name: FitMate, email: me@gmail.com} */
    static Map<String, String> parseSender(String from) {
        try {
            InternetAddress address = new InternetAddress(from, true);
            String name = address.getPersonal() == null ? "FitMate" : address.getPersonal();
            return Map.of("name", name, "email", address.getAddress());
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM 형식이 올바르지 않습니다: " + from, e);
        }
    }
}

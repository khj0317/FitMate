package com.fitmate.global.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** SMTP로 보낸다 (Brevo API 키가 없을 때). 로컬 개발에서는 Mailpit이 받아서 http://localhost:8025 에 보여 준다 */
@Component
@ConditionalOnExpression("'${fitmate.mail.brevo-api-key:}' == ''")
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailTransport(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("메일 작성 실패", e);
        }
    }
}

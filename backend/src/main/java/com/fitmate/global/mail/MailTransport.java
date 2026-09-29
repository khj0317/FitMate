package com.fitmate.global.mail;

/**
 * 메일을 실제로 보내는 방법. 메일 내용(TemplateAccountMailSender)과 분리해서 설정으로 바꿔 끼운다.
 * - BREVO_API_KEY가 있으면 Brevo HTTP API (Render 무료 플랜은 SMTP 포트 25·465·587을 막아서 HTTPS로 보내야 함)
 * - 없으면 SMTP (로컬은 docker-compose의 Mailpit)
 */
public interface MailTransport {

    void send(String to, String subject, String html);
}

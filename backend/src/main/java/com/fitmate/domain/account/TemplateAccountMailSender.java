package com.fitmate.domain.account;

import com.fitmate.global.mail.MailTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 계정 관련 메일 내용을 만들고 MailTransport(SMTP 또는 Brevo API)로 보낸다. 비동기(@Async)로 보내서
 * 1) 메일 서버가 느려도 API 응답이 늦어지지 않고
 * 2) 가입된 이메일인지에 따라 응답 시간이 달라져 계정 존재 여부가 드러나는 것을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateAccountMailSender implements AccountMailSender {

    private final MailTransport transport;

    @Async
    @Override
    public void sendLoginId(String email, String loginId) {
        send(email, "[FitMate] 아이디 안내", """
                <p>안녕하세요, FitMate입니다.</p>
                <p>요청하신 아이디는 아래와 같습니다.</p>
                <p style="font-size:22px;font-weight:700;letter-spacing:1px;color:#f03e0a">%s</p>
                <p style="color:#78726b;font-size:13px">본인이 요청하지 않았다면 이 메일을 무시하세요.</p>
                """.formatted(loginId));
    }

    @Async
    @Override
    public void sendPasswordResetCode(String email, String code) {
        send(email, "[FitMate] 비밀번호 재설정 인증 코드", codeMail(
                "비밀번호 재설정 인증 코드입니다. 10분 안에 입력해 주세요.", code,
                "본인이 요청하지 않았다면 이 메일을 무시하세요. 비밀번호는 바뀌지 않습니다."));
    }

    @Async
    @Override
    public void sendSignupVerificationCode(String email, String code) {
        send(email, "[FitMate] 이메일 인증 코드", codeMail(
                "이메일 인증 코드입니다. 10분 안에 입력해 주세요.", code,
                "본인이 요청하지 않았다면 이 메일을 무시하세요."));
    }

    private static String codeMail(String guide, String code, String footer) {
        return """
                <p>안녕하세요, FitMate입니다.</p>
                <p>%s</p>
                <p style="font-size:28px;font-weight:700;letter-spacing:6px;color:#f03e0a">%s</p>
                <p style="color:#78726b;font-size:13px">%s</p>
                """.formatted(guide, code, footer);
    }

    private void send(String to, String subject, String html) {
        try {
            transport.send(to, subject, html);
        } catch (RuntimeException e) {
            // 비동기라 사용자에게 에러를 돌려줄 수 없으므로 기록만 남긴다
            log.error("메일 발송 실패: subject={}", subject, e);
        }
    }
}

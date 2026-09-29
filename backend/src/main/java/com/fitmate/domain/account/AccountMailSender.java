package com.fitmate.domain.account;

/**
 * 계정 관련 메일 발송 (아이디 찾기, 비밀번호 재설정, 이메일 인증). 테스트에서는 실제로 보내지 않고 기록만 하는 구현으로 바꿔 끼운다.
 */
public interface AccountMailSender {

    void sendLoginId(String email, String loginId);

    void sendPasswordResetCode(String email, String code);

    void sendSignupVerificationCode(String email, String code);
}

package com.fitmate.domain.account;

/**
 * 계정 찾기 메일 발송. 테스트에서는 실제로 보내지 않고 기록만 하는 구현으로 바꿔 끼운다.
 */
public interface AccountMailSender {

    void sendLoginId(String email, String loginId);

    void sendPasswordResetCode(String email, String code);
}

package com.fitmate.support;

import com.fitmate.domain.account.AccountMailSender;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 메일을 실제로 보내지 않고 받는 사람별 마지막 내용을 기록한다 (동기 실행이라 바로 확인 가능). */
public class RecordingAccountMailSender implements AccountMailSender {

    private final Map<String, String> loginIds = new ConcurrentHashMap<>();
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();
    private final Map<String, String> signupCodes = new ConcurrentHashMap<>();

    @Override
    public void sendLoginId(String email, String loginId) {
        loginIds.put(email, loginId);
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        resetCodes.put(email, code);
    }

    @Override
    public void sendSignupVerificationCode(String email, String code) {
        signupCodes.put(email, code);
    }

    public Optional<String> signupCodeSentTo(String email) {
        return Optional.ofNullable(signupCodes.get(email));
    }

    public Optional<String> loginIdSentTo(String email) {
        return Optional.ofNullable(loginIds.get(email));
    }

    public Optional<String> resetCodeSentTo(String email) {
        return Optional.ofNullable(resetCodes.get(email));
    }
}

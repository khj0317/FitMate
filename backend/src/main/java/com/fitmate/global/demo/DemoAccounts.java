package com.fitmate.global.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 데모 계정(demo01 ~ demo30)의 비밀번호는 공개 저장소(DemoDataInitializer)에 적혀 있다.
 * 그래서 배포 서버에서는 누구든 로그인해서 계정을 바꿔 버릴 수 없도록 로그인·계정 찾기를 막는다.
 * 로컬 개발과 CI(E2E)에서만 DEMO_LOGIN_ENABLED로 연다.
 */
@Component
public class DemoAccounts {

    private static final Pattern LOGIN_ID = Pattern.compile("demo\\d{2}");

    private final boolean loginEnabled;

    public DemoAccounts(@Value("${fitmate.demo-data.login-enabled:false}") boolean loginEnabled) {
        this.loginEnabled = loginEnabled;
    }

    public boolean locked(String loginId) {
        return !loginEnabled && LOGIN_ID.matcher(loginId).matches();
    }
}

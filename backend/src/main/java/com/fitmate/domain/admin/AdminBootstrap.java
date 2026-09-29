package com.fitmate.domain.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 서버가 뜰 때 ADMIN_LOGIN_IDS에 적힌 아이디를 관리자로 맞춘다 (코드나 DB를 직접 고치지 않고 환경 변수로 관리).
 * 목록에서 빠진 사람의 권한은 건드리지 않는다. 뺏으려면 DB에서 직접 USER로 바꾼다.
 * 데모 데이터(데모 사용자)보다 나중에 실행되도록 순서를 늦춘다.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final JdbcClient jdbcClient;

    @Value("${fitmate.admin.login-ids:}")
    private String loginIds;

    @Override
    public void run(ApplicationArguments args) {
        List<String> ids = Arrays.stream(loginIds.split(","))
                .map(id -> id.strip().toLowerCase(Locale.ROOT))
                .filter(id -> !id.isEmpty())
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        int promoted = jdbcClient.sql("UPDATE users SET role = 'ADMIN' WHERE login_id IN (:ids) AND role <> 'ADMIN'")
                .param("ids", ids)
                .update();
        if (promoted > 0) {
            log.info("관리자 권한을 부여했습니다: {}명", promoted);
        }
    }
}

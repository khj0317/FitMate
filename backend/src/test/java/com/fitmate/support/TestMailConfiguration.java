package com.fitmate.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 SMTP 발송기 대신 기록용 발송기를 AccountMailSender로 주입한다. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean
    @Primary
    RecordingAccountMailSender recordingAccountMailSender() {
        return new RecordingAccountMailSender();
    }
}

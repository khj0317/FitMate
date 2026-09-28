package com.fitmate.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * - @Async: 메일 발송처럼 응답을 기다릴 필요가 없는 작업 (Spring Boot 기본 스레드 풀)
 * - @Scheduled: 접속 상태 heartbeat
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}

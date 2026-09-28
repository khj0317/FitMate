package com.fitmate.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 메일 발송처럼 응답을 기다릴 필요가 없는 작업을 비동기로 처리한다 (Spring Boot 기본 스레드 풀 사용). */
@Configuration
@EnableAsync
public class AsyncConfig {
}

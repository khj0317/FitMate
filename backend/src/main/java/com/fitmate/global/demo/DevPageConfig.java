package com.fitmate.global.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬(local 프로필)에서만 개발용 테스트 페이지(/dev/**)를 제공한다. 배포 환경에서는 404.
 */
@Configuration
@Profile("local")
public class DevPageConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/dev/**").addResourceLocations("classpath:/dev/");
    }
}

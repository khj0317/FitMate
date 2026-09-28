package com.fitmate.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Configuration
public class StorageConfig implements WebMvcConfigurer {

    private final StorageProperties properties;
    private final FileStorage fileStorage;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
        this.fileStorage = properties.isS3()
                ? new S3FileStorage(properties.s3())
                : new LocalFileStorage(Path.of(properties.local().dir()), properties.local().publicBaseUrl());
        log.info("사진 저장소: {}", properties.isS3() ? "S3 호환 (" + properties.s3().bucket() + ")" : "로컬 폴더 " + properties.local().dir());
    }

    @Bean
    public FileStorage fileStorage() {
        return fileStorage;
    }

    /** 로컬 저장소일 때만 /files/** 로 파일을 제공한다. 파일 이름이 UUID라 1년 캐시해도 안전하다 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (fileStorage instanceof LocalFileStorage local) {
            registry.addResourceHandler(LocalFileStorage.URL_PATH + "**")
                    .addResourceLocations(local.root().toUri().toString())
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        }
    }
}

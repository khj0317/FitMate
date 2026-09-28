package com.fitmate.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String type, Local local, S3 s3) {

    public boolean isS3() {
        return "s3".equalsIgnoreCase(type);
    }

    public record Local(String dir, String publicBaseUrl) {
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey,
                     String publicUrl, boolean pathStyle) {
    }
}

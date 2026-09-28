package com.fitmate.global.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.Optional;

/**
 * S3 호환 저장소 (Supabase Storage, AWS S3, Cloudflare R2, MinIO).
 * 버킷은 공개 읽기로 두고, 파일 이름이 추측할 수 없는 UUID라 URL을 아는 사람만 볼 수 있다.
 */
public class S3FileStorage implements FileStorage {

    /** 파일 이름이 UUID라 내용이 바뀌지 않으므로 브라우저·CDN이 1년 동안 캐시해도 된다 */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String bucket;
    private final String publicUrl;

    public S3FileStorage(StorageProperties.S3 properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(properties.pathStyle()); // Supabase·MinIO는 path-style 주소만 지원
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        this.client = builder.build();
        this.bucket = properties.bucket();
        this.publicUrl = properties.publicUrl().endsWith("/") ? properties.publicUrl() : properties.publicUrl() + "/";
    }

    @Override
    public String store(String key, byte[] data, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(data));
        return publicUrl + key;
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public Optional<String> keyOf(String url) {
        return url != null && url.startsWith(publicUrl) ? Optional.of(url.substring(publicUrl.length())) : Optional.empty();
    }
}

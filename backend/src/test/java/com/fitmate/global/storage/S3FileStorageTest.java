package com.fitmate.global.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배포용 S3 호환 저장소 구현을 실제 S3 API 서버(Adobe S3Mock 컨테이너)로 검증한다.
 * Supabase Storage도 같은 S3 API(path-style)를 쓴다.
 */
class S3FileStorageTest {

    private static final String BUCKET = "fitmate-test";
    private static final String PUBLIC_URL = "https://cdn.example.com/storage/v1/object/public/fitmate-test/";

    private static final int S3_PORT = 9090;

    private static GenericContainer<?> s3mock;
    private static S3Client client;
    private static S3FileStorage storage;

    @BeforeAll
    static void startS3Mock() {
        s3mock = new GenericContainer<>("adobe/s3mock:latest")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/").forPort(S3_PORT));
        s3mock.start();
        String endpoint = "http://%s:%d".formatted(s3mock.getHost(), s3mock.getMappedPort(S3_PORT));

        client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
        client.createBucket(builder -> builder.bucket(BUCKET));
        storage = new S3FileStorage(new StorageProperties.S3(endpoint, "us-east-1", BUCKET,
                "test", "test", PUBLIC_URL, true));
    }

    @AfterAll
    static void stopS3Mock() {
        client.close();
        s3mock.stop();
    }

    @Test
    @DisplayName("저장하면 공개 URL을 돌려주고, 저장된 내용·타입·캐시 설정이 맞다")
    void storeAndRead() {
        byte[] data = {1, 2, 3, 4};

        String url = storage.store("chat/2026/09/abc.jpg", data, "image/jpeg");

        assertThat(url).isEqualTo(PUBLIC_URL + "chat/2026/09/abc.jpg");
        ResponseBytes<GetObjectResponse> stored = client.getObjectAsBytes(b -> b.bucket(BUCKET).key("chat/2026/09/abc.jpg"));
        assertThat(stored.asByteArray()).isEqualTo(data);
        assertThat(stored.response().contentType()).isEqualTo("image/jpeg");
        assertThat(stored.response().cacheControl()).contains("immutable");
    }

    @Test
    @DisplayName("URL에서 저장 키를 찾아 삭제할 수 있고, 외부 URL은 무시한다")
    void keyOfAndDelete() {
        String url = storage.store("profile/2026/09/me.jpg", new byte[]{9}, "image/jpeg");

        assertThat(storage.keyOf(url)).contains("profile/2026/09/me.jpg");
        assertThat(storage.keyOf("https://other.example.com/x.jpg")).isEmpty();

        storage.delete("profile/2026/09/me.jpg");
        assertThatThrownBy(() -> client.getObjectAsBytes(b -> b.bucket(BUCKET).key("profile/2026/09/me.jpg")))
                .isInstanceOf(NoSuchKeyException.class);
    }
}

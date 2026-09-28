package com.fitmate.global.image;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.support.TestImages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    @DisplayName("긴 변이 최대 크기를 넘으면 비율을 유지하며 줄이고 JPEG로 저장한다")
    void resizeKeepingAspectRatio() {
        ProcessedImage result = processor.process(TestImages.png(3000, 2000, false), 1600, false);

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(1067);
        BufferedImage decoded = TestImages.read(result.data());
        assertThat(decoded.getWidth()).isEqualTo(1600);
    }

    @Test
    @DisplayName("작은 사진은 키우지 않는다")
    void doNotUpscale() {
        ProcessedImage result = processor.process(TestImages.jpeg(300, 200), 1600, false);

        assertThat(result.width()).isEqualTo(300);
        assertThat(result.height()).isEqualTo(200);
    }

    @Test
    @DisplayName("프로필 사진은 가운데를 정사각형으로 잘라 줄인다")
    void squareCrop() {
        ProcessedImage result = processor.process(TestImages.png(1000, 600, false), 512, true);

        assertThat(result.width()).isEqualTo(512);
        assertThat(result.height()).isEqualTo(512);
    }

    @Test
    @DisplayName("투명 배경 PNG는 PNG로 남긴다")
    void keepTransparentPng() {
        ProcessedImage result = processor.process(TestImages.png(200, 200, true), 1600, false);

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(TestImages.read(result.data()).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    @DisplayName("다시 인코딩하면서 EXIF(촬영 위치 GPS)를 지운다")
    void stripExif() {
        byte[] withGps = TestImages.jpegWithGpsExif(400, 300);
        assertThat(new String(withGps, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");

        ProcessedImage result = processor.process(withGps, 1600, false);

        String output = new String(result.data(), StandardCharsets.ISO_8859_1);
        assertThat(output).doesNotContain("GPSLatitude").doesNotContain("Exif");
    }

    @Test
    @DisplayName("확장자와 상관없이 내용이 JPEG/PNG가 아니면 거절한다")
    void rejectNonImage() {
        assertThatThrownBy(() -> processor.process("hello, not an image".getBytes(), 1600, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE);

        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
        assertThatThrownBy(() -> processor.process(gif, 1600, false))
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE);
    }

    @Test
    @DisplayName("헤더에 거대한 크기가 적힌 이미지는 풀기 전에 거절한다 (압축 폭탄 방지)")
    void rejectDecompressionBomb() {
        byte[] bomb = TestImages.pngHeaderOnly(20_000, 20_000);

        assertThatThrownBy(() -> processor.process(bomb, 1600, false))
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    @DisplayName("깨진 JPEG는 거절한다")
    void rejectCorruptedJpeg() {
        byte[] jpeg = TestImages.jpeg(100, 100);
        byte[] corrupted = java.util.Arrays.copyOf(jpeg, 20);

        assertThatThrownBy(() -> processor.process(corrupted, 1600, false))
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_IMAGE);
    }
}

package com.fitmate.global.image;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 업로드된 사진을 검증하고 다시 인코딩한다.
 * - 확장자·Content-Type이 아니라 파일 앞부분(매직 넘버)으로 JPEG/PNG인지 확인한다.
 * - 픽셀 크기를 먼저 읽어 너무 큰 이미지는 디코딩 전에 거절한다 (작은 파일이 거대한 이미지로 풀리는 압축 폭탄 방지).
 * - 다시 인코딩하면서 EXIF(촬영 위치 GPS, 기기 정보)가 모두 빠진다. 위치 기반 서비스라 특히 중요하다.
 *   사진 회전(EXIF Orientation)은 브라우저가 업로드 전에 적용해서 보낸다.
 */
@Component
public class ImageProcessor {

    static final int MAX_SOURCE_SIDE = 8_000;
    static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final long WAIT_SECONDS = 20;

    /**
     * 사진 디코딩·인코딩은 JVM 힙 밖의 네이티브 메모리까지 크게 쓴다. 메모리가 작은 서버(예: 512MB)에서
     * 여러 장이 한꺼번에 처리되면 컨테이너가 강제 종료되므로, 동시에 처리하는 장수를 제한하고 나머지는 잠시 기다리게 한다
     */
    private final Semaphore permits;

    public ImageProcessor() {
        this(4);
    }

    @Autowired
    public ImageProcessor(@Value("${fitmate.image.max-concurrency:4}") int maxConcurrency) {
        this.permits = new Semaphore(Math.max(1, maxConcurrency), true);
    }

    public ProcessedImage process(byte[] data, int maxSide, boolean squareCrop) {
        Format format = detect(data);
        acquire();
        try {
            return processNow(data, format, maxSide, squareCrop);
        } finally {
            permits.release();
        }
    }

    private void acquire() {
        try {
            if (!permits.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "사진을 올리는 사람이 많아요. 잠시 후 다시 시도해 주세요.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "사진을 올리는 사람이 많아요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private ProcessedImage processNow(byte[] data, Format format, int maxSide, boolean squareCrop) {
        BufferedImage image = read(data, maxSide, squareCrop);

        if (squareCrop) {
            int side = Math.min(image.getWidth(), image.getHeight());
            image = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
        }
        image = resize(image, maxSide);

        // 투명 배경이 있는 PNG만 PNG로, 나머지는 용량이 작은 JPEG로 저장한다
        boolean keepPng = format == Format.PNG && image.getColorModel().hasAlpha();
        return keepPng
                ? new ProcessedImage(write(image, "png", null), "png", "image/png", image.getWidth(), image.getHeight())
                : new ProcessedImage(write(toRgb(image), "jpeg", JPEG_QUALITY), "jpg", "image/jpeg", image.getWidth(), image.getHeight());
    }

    private enum Format { JPEG, PNG }

    private static Format detect(byte[] data) {
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Format.PNG;
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
    }

    /**
     * 결과 크기의 2배보다 훨씬 큰 사진은 읽을 때부터 건너뛰며(subsampling) 읽는다.
     * 예: 8000px 사진을 1600px로 줄일 때 원본(최대 160MB)을 다 펼치지 않고 약 3200px(약 1/4)로 읽어서 메모리를 크게 아낀다.
     * 2배 여유를 두고 읽은 뒤 resize()에서 부드럽게 줄이므로 화질 차이는 거의 없다
     */
    private static BufferedImage read(byte[] data, int maxSide, boolean squareCrop) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true); // 메타데이터는 읽지 않는다
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
                }
                // 정사각형으로 자르는 프로필 사진은 짧은 변이 결과 크기가 된다
                int relevantSide = squareCrop ? Math.min(width, height) : Math.max(width, height);
                int factor = Math.max(1, relevantSide / (maxSide * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(factor, factor, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE); // 깨진 파일, CMYK JPEG 등
        }
    }

    /** 긴 변이 maxSide가 되도록 줄인다. 한 번에 크게 줄이면 계단 현상이 생겨 절반씩 여러 번 줄인다 */
    static BufferedImage resize(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (Math.max(width, height) <= maxSide) {
            return source;
        }
        double scale = (double) maxSide / Math.max(width, height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return target;
    }

    /** JPEG는 투명도를 지원하지 않으므로 흰 배경 위에 그려 RGB로 바꾼다 */
    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] write(BufferedImage image, String format, Float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (quality != null) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param); // 메타데이터 없이 저장
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

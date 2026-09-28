package com.fitmate.global.image;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
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

    public ProcessedImage process(byte[] data, int maxSide, boolean squareCrop) {
        Format format = detect(data);
        BufferedImage image = read(data);

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

    private static BufferedImage read(byte[] data) {
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
                return reader.read(0);
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

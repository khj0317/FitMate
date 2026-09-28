package com.fitmate.global.image;

public record ProcessedImage(byte[] data, String extension, String contentType, int width, int height) {
}

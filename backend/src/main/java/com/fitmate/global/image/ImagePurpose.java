package com.fitmate.global.image;

/** 용도별 저장 폴더와 최대 크기 */
public enum ImagePurpose {
    CHAT("chat", 1600, false),
    PROFILE("profile", 512, true);

    private final String folder;
    private final int maxSide;
    private final boolean squareCrop;

    ImagePurpose(String folder, int maxSide, boolean squareCrop) {
        this.folder = folder;
        this.maxSide = maxSide;
        this.squareCrop = squareCrop;
    }

    public String folder() {
        return folder;
    }

    public int maxSide() {
        return maxSide;
    }

    public boolean squareCrop() {
        return squareCrop;
    }
}

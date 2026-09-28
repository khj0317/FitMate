package com.fitmate.domain.location;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class AreaNames {

    private static final Pattern DONG_LEVEL = Pattern.compile(".*(동|읍|면|가|리)$");
    private static final Pattern HAS_DIGIT_ONLY = Pattern.compile("^[\\d-]+$");

    private AreaNames() {
    }

    /**
     * 지번 주소에서 동 단위까지만 잘라 활동 지역명으로 쓴다. 번지는 개인 위치라 저장하지 않는다.
     * "서울 성동구 성수동2가 289-5" → "서울 성동구 성수동2가"
     * "경기 성남시 분당구 삼평동 681" → "경기 성남시 분당구 삼평동"
     */
    static String fromAddress(String address) {
        List<String> parts = new ArrayList<>();
        for (String token : address.trim().split("\\s+")) {
            if (HAS_DIGIT_ONLY.matcher(token).matches()) {
                break;
            }
            parts.add(token);
            if (parts.size() >= 2 && DONG_LEVEL.matcher(token).matches()) {
                break;
            }
        }
        return String.join(" ", parts);
    }
}

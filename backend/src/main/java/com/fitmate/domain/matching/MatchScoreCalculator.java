package com.fitmate.domain.matching;

import com.fitmate.domain.user.SkillLevel;

import java.math.BigDecimal;

/**
 * 매칭 점수(0~100)를 계산한다. DB나 스프링에 의존하지 않는 순수 함수라 단위 테스트로 가중치를 검증한다.
 *
 * <pre>
 * 거리 35점 : 가까울수록 높음 (검색 반경 끝이면 0점)
 * 실력 30점 : 같은 수준 30, 한 단계 차이 15, 두 단계 차이 0 (내 실력을 모르면 15)
 * 시간 25점 : 주간 겹치는 운동 가능 시간이 3시간 이상이면 만점
 * 매너 10점 : 매너 점수 30 이하 0점 ~ 50 이상 만점
 * </pre>
 */
public final class MatchScoreCalculator {

    static final double DISTANCE_WEIGHT = 35;
    static final double SKILL_WEIGHT = 30;
    static final double TIME_WEIGHT = 25;
    static final double MANNER_WEIGHT = 10;

    static final long FULL_OVERLAP_MINUTES = 180;
    static final double MANNER_MIN = 30.0;
    static final double MANNER_MAX = 50.0;

    private MatchScoreCalculator() {
    }

    public static Score calculate(double distanceMeters, double radiusMeters,
                                  SkillLevel myLevel, SkillLevel theirLevel,
                                  long overlapMinutesPerWeek, BigDecimal mannerScore) {
        double distance = DISTANCE_WEIGHT * (1 - clamp(distanceMeters / radiusMeters));
        double skill = SKILL_WEIGHT * skillRatio(myLevel, theirLevel);
        double time = TIME_WEIGHT * clamp((double) overlapMinutesPerWeek / FULL_OVERLAP_MINUTES);
        double manner = MANNER_WEIGHT * clamp((mannerScore.doubleValue() - MANNER_MIN) / (MANNER_MAX - MANNER_MIN));

        return new Score(
                (int) Math.round(distance + skill + time + manner),
                (int) Math.round(distance),
                (int) Math.round(skill),
                (int) Math.round(time),
                (int) Math.round(manner)
        );
    }

    static double skillRatio(SkillLevel myLevel, SkillLevel theirLevel) {
        if (myLevel == null) {
            return 0.5;
        }
        int gap = Math.abs(myLevel.ordinal() - theirLevel.ordinal());
        return switch (gap) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            default -> 0.0;
        };
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record Score(int total, int distance, int skill, int time, int manner) {
    }
}

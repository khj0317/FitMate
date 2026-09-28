package com.fitmate.domain.matching;

import com.fitmate.domain.user.SkillLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MatchScoreCalculatorTest {

    private static final double RADIUS = 5_000;
    private static final BigDecimal INITIAL_MANNER = new BigDecimal("36.5");

    @Test
    @DisplayName("모든 조건이 최고면 100점")
    void perfectMatch() {
        var score = MatchScoreCalculator.calculate(0, RADIUS, SkillLevel.BEGINNER, SkillLevel.BEGINNER,
                180, new BigDecimal("50.0"));

        assertThat(score).isEqualTo(new MatchScoreCalculator.Score(100, 35, 30, 25, 10));
    }

    @Test
    @DisplayName("반경 끝, 실력 두 단계 차이, 겹치는 시간 없음, 매너 30 이하면 0점")
    void worstMatch() {
        var score = MatchScoreCalculator.calculate(RADIUS, RADIUS, SkillLevel.BEGINNER, SkillLevel.ADVANCED,
                0, new BigDecimal("20.0"));

        assertThat(score.total()).isZero();
    }

    @Test
    @DisplayName("가입 직후 매너 점수(36.5)는 10점 중 3점")
    void initialMannerScore() {
        var score = MatchScoreCalculator.calculate(0, RADIUS, null, SkillLevel.BEGINNER, 0, INITIAL_MANNER);

        assertThat(score.manner()).isEqualTo(3);
    }

    @Test
    @DisplayName("거리 점수는 반경에 비례해 줄어든다")
    void distanceScoreDecreasesLinearly() {
        var half = MatchScoreCalculator.calculate(RADIUS / 2, RADIUS, null, SkillLevel.BEGINNER, 0, INITIAL_MANNER);
        var outside = MatchScoreCalculator.calculate(RADIUS * 2, RADIUS, null, SkillLevel.BEGINNER, 0, INITIAL_MANNER);

        assertThat(half.distance()).isEqualTo(18);   // 35 * 0.5 = 17.5 → 반올림
        assertThat(outside.distance()).isZero();     // 반경 밖이어도 음수가 되지 않는다
    }

    @Test
    @DisplayName("겹치는 시간은 3시간까지만 점수에 반영된다")
    void timeScoreIsCapped() {
        var ninetyMinutes = MatchScoreCalculator.calculate(0, RADIUS, null, SkillLevel.BEGINNER, 90, INITIAL_MANNER);
        var tenHours = MatchScoreCalculator.calculate(0, RADIUS, null, SkillLevel.BEGINNER, 600, INITIAL_MANNER);

        assertThat(ninetyMinutes.time()).isEqualTo(13); // 25 * 0.5 = 12.5 → 반올림
        assertThat(tenHours.time()).isEqualTo(25);
    }

    @ParameterizedTest(name = "{0} vs {1} → {2}")
    @CsvSource({
            "BEGINNER, BEGINNER, 1.0",
            "BEGINNER, INTERMEDIATE, 0.5",
            "ADVANCED, INTERMEDIATE, 0.5",
            "BEGINNER, ADVANCED, 0.0",
            ", ADVANCED, 0.5"
    })
    @DisplayName("실력 차이가 적을수록 실력 점수가 높고, 내 실력을 모르면 중간값")
    void skillRatio(SkillLevel mine, SkillLevel theirs, double expected) {
        assertThat(MatchScoreCalculator.skillRatio(mine, theirs)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}m → {1}km")
    @CsvSource({"0, 0.5", "499, 0.5", "500, 0.5", "501, 1.0", "1000, 1.0", "1001, 1.5", "7300, 7.5"})
    @DisplayName("거리는 위치 역추적을 막기 위해 0.5km 단위로 올림한다")
    void roundUpDistance(double meters, double expectedKm) {
        assertThat(MatchingService.roundUpDistance(meters)).isEqualTo(expectedKm);
    }
}

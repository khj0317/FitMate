package com.fitmate.domain.matching.dto;

import com.fitmate.domain.matching.MatchScoreCalculator;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;

import java.math.BigDecimal;
import java.util.List;

public record MatchCandidateResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        Gender gender,
        String ageGroup,
        String activityAreaName,
        BigDecimal mannerScore,
        double approximateDistanceKm,
        long overlapMinutesPerWeek,
        int matchScore,
        ScoreDetail scoreDetail,
        List<CommonSport> commonSports
) {

    public record ScoreDetail(int distance, int skill, int time, int manner) {
        public static ScoreDetail from(MatchScoreCalculator.Score score) {
            return new ScoreDetail(score.distance(), score.skill(), score.time(), score.manner());
        }
    }

    public record CommonSport(Short sportId, String name, SkillLevel myLevel, SkillLevel theirLevel) {
    }
}

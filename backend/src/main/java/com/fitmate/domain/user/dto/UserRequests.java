package com.fitmate.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public final class UserRequests {

    private UserRequests() {
    }

    /** PATCH: null인 필드는 변경하지 않는다. */
    public record UpdateProfile(
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.")
            String nickname,

            @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
            String bio,

            @Size(max = 500) @URL(message = "올바른 URL 형식이 아닙니다.")
            String profileImageUrl,

            Gender gender,

            @Min(value = 1920, message = "출생 연도가 올바르지 않습니다.")
            @Max(value = 2015, message = "출생 연도가 올바르지 않습니다.")
            Short birthYear,

            @Min(value = 1, message = "검색 반경은 1~50km입니다.")
            @Max(value = 50, message = "검색 반경은 1~50km입니다.")
            Short searchRadiusKm
    ) {
    }

    public record UpdateLocation(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotBlank @Size(max = 100) String areaName
    ) {
    }

    public record UpdateSports(
            @NotNull @Size(max = 5, message = "운동 종목은 최대 5개까지 등록할 수 있습니다.")
            List<@Valid @NotNull SportLevel> sports
    ) {
    }

    public record SportLevel(@NotNull Short sportId, @NotNull SkillLevel skillLevel) {
    }

    public record UpdateAvailableTimes(
            @NotNull @Size(max = 21, message = "운동 가능 시간대는 최대 21개까지 등록할 수 있습니다.")
            List<@Valid @NotNull AvailableTime> availableTimes
    ) {
    }

    public record AvailableTime(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}

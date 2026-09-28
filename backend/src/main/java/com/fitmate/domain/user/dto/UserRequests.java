package com.fitmate.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.DayOfWeek;
import java.time.LocalDate;
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

            @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 255)
            String email,

            Gender gender,

            @Past(message = "생년월일이 올바르지 않습니다.")
            LocalDate birthDate,

            @Min(value = 1, message = "검색 반경은 1~50km입니다.")
            @Max(value = 50, message = "검색 반경은 1~50km입니다.")
            Short searchRadiusKm
    ) {
    }

    /**
     * 프로필 전체를 한 번에 저장한다 (PUT). 하나의 트랜잭션이라 중간에 실패하면 모두 취소된다.
     * bio는 비우면 삭제된다. 이메일은 계정 찾기에 쓰이므로 필수.
     */
    public record UpdateAll(
            @NotBlank
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.")
            String nickname,

            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 255)
            String email,

            @Size(max = 500, message = "자기소개는 500자 이하여야 합니다.")
            String bio,

            @NotNull(message = "성별을 선택해 주세요.")
            Gender gender,

            @NotNull(message = "생년월일을 입력해 주세요.")
            @Past(message = "생년월일이 올바르지 않습니다.")
            LocalDate birthDate,

            @NotNull
            @Min(value = 1, message = "검색 반경은 1~50km입니다.")
            @Max(value = 50, message = "검색 반경은 1~50km입니다.")
            Short searchRadiusKm,

            @NotNull(message = "활동 지역을 선택해 주세요.") @Valid
            UpdateLocation location,

            @NotNull @Size(max = 5, message = "운동 종목은 최대 5개까지 등록할 수 있습니다.")
            List<@Valid @NotNull SportLevel> sports,

            @NotNull @Size(max = 21, message = "운동 가능 시간대는 최대 21개까지 등록할 수 있습니다.")
            List<@Valid @NotNull AvailableTime> availableTimes
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

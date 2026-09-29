package com.fitmate.domain.gathering.dto;

import com.fitmate.domain.gathering.Gathering;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GatheringDtos {

    private GatheringDtos() {
    }

    public record Create(
            @NotNull(message = "운동 종목을 선택해 주세요.") Short sportId,
            @NotBlank(message = "모임 이름을 입력해 주세요.") @Size(max = 100, message = "모임 이름은 100자 이하여야 합니다.") String title,
            @Size(max = 2000, message = "소개는 2000자 이하여야 합니다.") String description,
            @NotBlank(message = "만날 장소를 입력해 주세요.") @Size(max = 100) String placeName,
            @NotNull @Valid Point location,
            @NotNull(message = "모임 시간을 선택해 주세요.") Instant startsAt,
            @NotNull @Min(value = 2, message = "정원은 2~50명입니다.") @Max(value = 50, message = "정원은 2~50명입니다.") Short capacity
    ) {
    }

    public record Point(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {
    }

    public record Host(Long userId, String nickname, String profileImageUrl, BigDecimal mannerScore) {
    }

    /** 목록 한 줄. distanceKm는 위치 보호를 위해 0.5km 단위로 올림 */
    public record Summary(
            Long id,
            String title,
            Short sportId,
            String sportCode,
            String sportName,
            String placeName,
            Instant startsAt,
            short capacity,
            short currentCount,
            Gathering.Status status,
            Host host,
            Double distanceKm,
            boolean joined
    ) {
    }

    public record Participant(Long userId, String nickname, String profileImageUrl, BigDecimal mannerScore, boolean host) {
    }

    public record Detail(
            Summary summary,
            String description,
            double latitude,
            double longitude,
            List<Participant> participants,
            boolean isHost,
            Long chatRoomId
    ) {
    }

    public record Joined(Long gatheringId, Long chatRoomId) {
    }
}

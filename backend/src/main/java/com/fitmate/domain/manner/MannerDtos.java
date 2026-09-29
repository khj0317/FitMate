package com.fitmate.domain.manner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class MannerDtos {

    private MannerDtos() {
    }

    /** gatheringId와 matchRequestId 중 하나만 채운다 */
    public record CreateReview(
            @NotNull Long targetId,
            Long gatheringId,
            Long matchRequestId,
            @NotNull MannerReview.Rating rating,
            @Size(max = 5) Set<MannerReview.Tag> tags
    ) {
    }

    /** 아직 평가하지 않은, 함께 운동한 상대 */
    public record Pending(
            Long targetId,
            String nickname,
            String profileImageUrl,
            Long gatheringId,
            Long matchRequestId,
            /* 모임 제목 또는 1:1 매칭 종목 이름 */
            String context,
            Instant happenedAt
    ) {
    }

    public record TagCount(MannerReview.Tag tag, long count) {
    }

    /** 공개 매너 정보. 아쉬운 태그는 공개하지 않는다 */
    public record Summary(BigDecimal mannerScore, long reviewCount, List<TagCount> tags) {
    }
}

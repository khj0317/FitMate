package com.fitmate.domain.manner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 함께 운동한 상대에 대한 매너 평가. 한 번 남기면 고칠 수 없다 */
@Entity
@Table(name = "manner_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MannerReview {

    public enum Rating {
        GOOD(new BigDecimal("0.5")), NORMAL(BigDecimal.ZERO), BAD(new BigDecimal("-0.5"));

        private final BigDecimal delta;

        Rating(BigDecimal delta) {
            this.delta = delta;
        }
    }

    public enum Tag {
        PUNCTUAL(true), KIND(true), SKILLED(true), TEACHES(true), FUN(true),
        LATE(false), NO_SHOW(false), RUDE(false);

        private final boolean positive;

        Tag(boolean positive) {
            this.positive = positive;
        }

        public boolean isPositive() {
            return positive;
        }
    }

    /** 노쇼는 약속 자체를 깬 것이라 추가로 크게 깎는다 */
    private static final BigDecimal NO_SHOW_PENALTY = new BigDecimal("-1.0");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reviewerId;

    @Column(nullable = false)
    private Long targetId;

    private Long gatheringId;

    private Long matchRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rating rating;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] tags;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal scoreDelta;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public MannerReview(Long reviewerId, Long targetId, Long gatheringId, Long matchRequestId,
                        Rating rating, Set<Tag> tags) {
        this.reviewerId = reviewerId;
        this.targetId = targetId;
        this.gatheringId = gatheringId;
        this.matchRequestId = matchRequestId;
        this.rating = rating;
        this.tags = tags.stream().map(Tag::name).sorted().toArray(String[]::new);
        this.scoreDelta = tags.contains(Tag.NO_SHOW) ? rating.delta.add(NO_SHOW_PENALTY) : rating.delta;
    }
}

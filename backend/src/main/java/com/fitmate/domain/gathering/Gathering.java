package com.fitmate.domain.gathering;

import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.user.User;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/**
 * 정원이 있는 모임. 참가 인원(currentCount)은 동시 참여 경쟁 때문에 엔티티로 바꾸지 않고
 * GatheringRepository의 조건부 UPDATE(tryReserveSeat / releaseSeat)로만 바꾼다.
 */
@Entity
@Table(name = "gatherings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gathering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    @Column(nullable = false, length = 100)
    private String placeName;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private short capacity;

    @Column(nullable = false)
    private short currentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Gathering(User host, Sport sport, String title, String description, Point location, String placeName,
                     Instant startsAt, short capacity) {
        this.host = host;
        this.sport = sport;
        this.title = title;
        this.description = description;
        this.location = location;
        this.placeName = placeName;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.currentCount = 1; // 모임장
        this.status = Status.RECRUITING;
    }

    public boolean isHost(Long userId) {
        return host.getId().equals(userId);
    }

    public boolean hasStarted(Instant now) {
        return !startsAt.isAfter(now);
    }

    public void cancel(Instant now) {
        if (status == Status.CANCELED) {
            throw new BusinessException(ErrorCode.GATHERING_NOT_FOUND);
        }
        if (hasStarted(now)) {
            throw new BusinessException(ErrorCode.GATHERING_ALREADY_STARTED);
        }
        status = Status.CANCELED;
    }

    public enum Status {
        RECRUITING,
        CLOSED,
        COMPLETED,
        CANCELED
    }
}

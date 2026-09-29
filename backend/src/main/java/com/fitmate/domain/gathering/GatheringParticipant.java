package com.fitmate.domain.gathering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "gathering_participants")
@IdClass(GatheringParticipant.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatheringParticipant {

    @Id
    private Long gatheringId;

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant joinedAt;

    public GatheringParticipant(Long gatheringId, Long userId) {
        this.gatheringId = gatheringId;
        this.userId = userId;
        this.status = Status.JOINED;
        this.joinedAt = Instant.now();
    }

    public boolean isJoined() {
        return status == Status.JOINED;
    }

    /** 나갔다가 다시 참여 */
    public void rejoin() {
        status = Status.JOINED;
        joinedAt = Instant.now();
    }

    public void leave() {
        status = Status.CANCELED;
    }

    public enum Status {
        JOINED,
        CANCELED,
        ATTENDED,
        NO_SHOW
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {
        private Long gatheringId;
        private Long userId;
    }
}

package com.fitmate.domain.user;

import com.fitmate.domain.sport.Sport;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Entity
@Table(name = "user_sports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSport {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("sportId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id")
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillLevel skillLevel;

    UserSport(User user, Sport sport, SkillLevel skillLevel) {
        this.user = user;
        this.sport = sport;
        this.skillLevel = skillLevel;
    }

    void changeSkillLevel(SkillLevel skillLevel) {
        this.skillLevel = skillLevel;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Id implements Serializable {
        private Long userId;
        private Short sportId;
    }
}

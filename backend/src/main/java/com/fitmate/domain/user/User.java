package com.fitmate.domain.user;

import com.fitmate.domain.sport.Sport;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 이보다 이른 생년월일은 잘못 입력한 것으로 본다 */
    public static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1920, 1, 1);

    private static final BigDecimal INITIAL_MANNER_SCORE = new BigDecimal("36.5");
    private static final short DEFAULT_SEARCH_RADIUS_KM = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String loginId;

    /** 아이디·비밀번호 찾기용. 신규 가입은 필수, 예전에 이메일 없이 가입한 계정만 null일 수 있다 */
    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(length = 500)
    private String bio;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point,4326)")
    private Point activityLocation;

    @Column(length = 100)
    private String activityAreaName;

    @Column(nullable = false)
    private short searchRadiusKm;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal mannerScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSport> sports = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserAvailableTime> availableTimes = new ArrayList<>();

    public User(String loginId, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.searchRadiusKm = DEFAULT_SEARCH_RADIUS_KM;
        this.mannerScore = INITIAL_MANNER_SCORE;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeBio(String bio) {
        this.bio = bio.isBlank() ? null : bio;
    }

    public void changeProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl.isBlank() ? null : profileImageUrl;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    /** 빈 문자열이면 이메일을 지운다 (선택 입력) */
    public void changeEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email;
    }

    public void changeSearchRadiusKm(short searchRadiusKm) {
        this.searchRadiusKm = searchRadiusKm;
    }

    public void changeActivityLocation(Point location, String areaName) {
        this.activityLocation = location;
        this.activityAreaName = areaName;
    }

    /**
     * 요청 목록과 현재 목록을 비교해 삭제/수정/추가만 반영한다.
     * 전체 삭제 후 재삽입하면 같은 (user_id, sport_id) 행의 INSERT가 DELETE보다 먼저 flush되어
     * PK 충돌이 날 수 있기 때문이다.
     */
    public void replaceSports(Map<Sport, SkillLevel> levelsBySport) {
        Map<Short, SkillLevel> levelsBySportId = levelsBySport.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getId(), Map.Entry::getValue));

        sports.removeIf(userSport -> !levelsBySportId.containsKey(userSport.getSport().getId()));

        Set<Short> existingSportIds = new HashSet<>();
        for (UserSport userSport : sports) {
            Short sportId = userSport.getSport().getId();
            userSport.changeSkillLevel(levelsBySportId.get(sportId));
            existingSportIds.add(sportId);
        }

        levelsBySport.forEach((sport, level) -> {
            if (!existingSportIds.contains(sport.getId())) {
                sports.add(new UserSport(this, sport, level));
            }
        });
    }

    public void replaceAvailableTimes(List<UserAvailableTime.Slot> slots) {
        availableTimes.clear();
        slots.forEach(slot -> availableTimes.add(new UserAvailableTime(this, slot)));
    }
}

package com.fitmate.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserAvailableTime;
import com.fitmate.domain.user.UserSport;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;

public final class UserResponses {

    private UserResponses() {
    }

    /** 본인 프로필: 아이디, 이메일, 생년월일, 정확한 좌표까지 포함 */
    public record MyProfile(
            Long id,
            String loginId,
            String email,
            String nickname,
            String bio,
            String profileImageUrl,
            Gender gender,
            LocalDate birthDate,
            Location location,
            short searchRadiusKm,
            BigDecimal mannerScore,
            List<Sport> sports,
            List<AvailableTime> availableTimes,
            /* ADMIN이면 웹에 관리자 메뉴가 보인다 */
            User.Role role
    ) {
        public static MyProfile from(User user) {
            return new MyProfile(
                    user.getId(),
                    user.getLoginId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getBio(),
                    user.getProfileImageUrl(),
                    user.getGender(),
                    user.getBirthDate(),
                    Location.from(user),
                    user.getSearchRadiusKm(),
                    user.getMannerScore(),
                    Sport.listOf(user),
                    AvailableTime.listOf(user),
                    user.getRole()
            );
        }
    }

    /** 다른 사용자에게 보이는 프로필: 아이디, 이메일, 좌표, 생년월일은 노출하지 않는다. */
    public record PublicProfile(
            Long id,
            String nickname,
            String bio,
            String profileImageUrl,
            Gender gender,
            String ageGroup,
            String activityAreaName,
            BigDecimal mannerScore,
            List<Sport> sports,
            List<AvailableTime> availableTimes
    ) {
        public static PublicProfile from(User user) {
            return new PublicProfile(
                    user.getId(),
                    user.getNickname(),
                    user.getBio(),
                    user.getProfileImageUrl(),
                    user.getGender(),
                    ageGroup(user.getBirthDate()),
                    user.getActivityAreaName(),
                    user.getMannerScore(),
                    Sport.listOf(user),
                    AvailableTime.listOf(user)
            );
        }

        /** 만 나이 기준 나이대 (예: 27세 → "20대") */
        public static String ageGroup(LocalDate birthDate) {
            if (birthDate == null) {
                return null;
            }
            int age = Period.between(birthDate, LocalDate.now()).getYears();
            return (age / 10 * 10) + "대";
        }
    }

    public record Location(double latitude, double longitude, String areaName) {
        static Location from(User user) {
            Point point = user.getActivityLocation();
            return point == null ? null : new Location(point.getY(), point.getX(), user.getActivityAreaName());
        }
    }

    public record Sport(Short sportId, String code, String name, SkillLevel skillLevel) {
        static List<Sport> listOf(User user) {
            return user.getSports().stream()
                    .map(Sport::from)
                    .sorted(Comparator.comparing(Sport::sportId))
                    .toList();
        }

        static Sport from(UserSport userSport) {
            var sport = userSport.getSport();
            return new Sport(sport.getId(), sport.getCode(), sport.getName(), userSport.getSkillLevel());
        }
    }

    public record AvailableTime(
            DayOfWeek dayOfWeek,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
        /** 컬렉션 배치 로딩 시 @OrderBy가 보장되지 않으므로 응답에서 정렬한다. */
        static List<AvailableTime> listOf(User user) {
            return user.getAvailableTimes().stream()
                    .map(AvailableTime::from)
                    .sorted(Comparator.comparing(AvailableTime::dayOfWeek).thenComparing(AvailableTime::startTime))
                    .toList();
        }

        static AvailableTime from(UserAvailableTime time) {
            return new AvailableTime(time.getDayOfWeek(), time.getStartTime(), time.getEndTime());
        }
    }
}

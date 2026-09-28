package com.fitmate.domain.user;

import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.dto.UserRequests;
import com.fitmate.domain.user.dto.UserResponses;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.util.GeoPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SportRepository sportRepository;

    public UserResponses.MyProfile getMyProfile(Long userId) {
        return UserResponses.MyProfile.from(getUser(userId));
    }

    public UserResponses.PublicProfile getPublicProfile(Long userId) {
        return UserResponses.PublicProfile.from(getUser(userId));
    }

    @Transactional
    public UserResponses.MyProfile updateProfile(Long userId, UserRequests.UpdateProfile request) {
        User user = getUser(userId);

        if (request.nickname() != null) {
            applyNickname(user, request.nickname());
        }
        if (request.email() != null) {
            applyEmail(user, request.email());
        }
        if (request.bio() != null) {
            user.changeBio(request.bio());
        }
        if (request.profileImageUrl() != null) {
            user.changeProfileImageUrl(request.profileImageUrl());
        }
        if (request.gender() != null) {
            user.changeGender(request.gender());
        }
        if (request.birthDate() != null) {
            applyBirthDate(user, request.birthDate());
        }
        if (request.searchRadiusKm() != null) {
            user.changeSearchRadiusKm(request.searchRadiusKm());
        }
        return UserResponses.MyProfile.from(user);
    }

    /** 프로필 화면의 "전체 저장": 모든 항목을 하나의 트랜잭션으로 저장한다. */
    @Transactional
    public UserResponses.MyProfile updateAll(Long userId, UserRequests.UpdateAll request) {
        User user = getUser(userId);
        applyNickname(user, request.nickname());
        applyEmail(user, request.email());
        user.changeBio(request.bio() == null ? "" : request.bio());
        user.changeGender(request.gender());
        applyBirthDate(user, request.birthDate());
        user.changeSearchRadiusKm(request.searchRadiusKm());
        applyLocation(user, request.location());
        applySports(user, request.sports());
        applyAvailableTimes(user, request.availableTimes());
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateLocation(Long userId, UserRequests.UpdateLocation request) {
        User user = getUser(userId);
        applyLocation(user, request);
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateSports(Long userId, UserRequests.UpdateSports request) {
        User user = getUser(userId);
        applySports(user, request.sports());
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateAvailableTimes(Long userId, UserRequests.UpdateAvailableTimes request) {
        User user = getUser(userId);
        applyAvailableTimes(user, request.availableTimes());
        return UserResponses.MyProfile.from(user);
    }

    // ---------- 항목별 검증 + 적용 (개별 API와 전체 저장 API가 같이 쓴다) ----------

    private void applyNickname(User user, String nickname) {
        if (nickname.equals(user.getNickname())) {
            return;
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(nickname);
    }

    /** 이메일은 아이디·비밀번호 찾기에 쓰이므로 지울 수 없다 */
    private void applyEmail(User user, String rawEmail) {
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw new BusinessException(ErrorCode.EMAIL_REQUIRED);
        }
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        user.changeEmail(email);
    }

    private void applyBirthDate(User user, LocalDate birthDate) {
        if (birthDate.isBefore(User.MIN_BIRTH_DATE)) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_DATE);
        }
        user.changeBirthDate(birthDate);
    }

    private void applyLocation(User user, UserRequests.UpdateLocation location) {
        user.changeActivityLocation(GeoPoints.of(location.latitude(), location.longitude()), location.areaName().strip());
    }

    private void applySports(User user, List<UserRequests.SportLevel> items) {
        List<Short> sportIds = items.stream().map(UserRequests.SportLevel::sportId).toList();
        if (sportIds.stream().distinct().count() != sportIds.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SPORT);
        }

        Map<Short, Sport> sportsById = sportRepository.findAllById(sportIds).stream()
                .collect(Collectors.toMap(Sport::getId, Function.identity()));
        if (sportsById.size() != sportIds.size()) {
            throw new BusinessException(ErrorCode.SPORT_NOT_FOUND);
        }

        Map<Sport, SkillLevel> levelsBySport = new LinkedHashMap<>();
        items.forEach(item -> levelsBySport.put(sportsById.get(item.sportId()), item.skillLevel()));
        user.replaceSports(levelsBySport);
    }

    private void applyAvailableTimes(User user, List<UserRequests.AvailableTime> times) {
        List<UserAvailableTime.Slot> slots = times.stream()
                .map(time -> new UserAvailableTime.Slot(time.dayOfWeek(), time.startTime(), time.endTime()))
                .sorted(Comparator.comparing(UserAvailableTime.Slot::dayOfWeek)
                        .thenComparing(UserAvailableTime.Slot::startTime))
                .toList();
        validateSlots(slots);
        user.replaceAvailableTimes(slots);
    }

    /** 정렬된 목록이므로 바로 이전 시간대와만 비교하면 겹침을 모두 찾을 수 있다. */
    private void validateSlots(List<UserAvailableTime.Slot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            UserAvailableTime.Slot slot = slots.get(i);
            if (!slot.startTime().isBefore(slot.endTime())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (i > 0 && slots.get(i - 1).overlaps(slot)) {
                throw new BusinessException(ErrorCode.OVERLAPPING_AVAILABLE_TIMES);
            }
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

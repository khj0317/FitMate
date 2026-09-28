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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            user.changeNickname(request.nickname());
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
        if (request.birthYear() != null) {
            user.changeBirthYear(request.birthYear());
        }
        if (request.searchRadiusKm() != null) {
            user.changeSearchRadiusKm(request.searchRadiusKm());
        }
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateLocation(Long userId, UserRequests.UpdateLocation request) {
        User user = getUser(userId);
        user.changeActivityLocation(GeoPoints.of(request.latitude(), request.longitude()), request.areaName());
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateSports(Long userId, UserRequests.UpdateSports request) {
        User user = getUser(userId);

        List<Short> sportIds = request.sports().stream().map(UserRequests.SportLevel::sportId).toList();
        if (sportIds.stream().distinct().count() != sportIds.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SPORT);
        }

        Map<Short, Sport> sportsById = sportRepository.findAllById(sportIds).stream()
                .collect(Collectors.toMap(Sport::getId, Function.identity()));
        if (sportsById.size() != sportIds.size()) {
            throw new BusinessException(ErrorCode.SPORT_NOT_FOUND);
        }

        Map<Sport, SkillLevel> levelsBySport = new LinkedHashMap<>();
        request.sports().forEach(item -> levelsBySport.put(sportsById.get(item.sportId()), item.skillLevel()));
        user.replaceSports(levelsBySport);
        return UserResponses.MyProfile.from(user);
    }

    @Transactional
    public UserResponses.MyProfile updateAvailableTimes(Long userId, UserRequests.UpdateAvailableTimes request) {
        User user = getUser(userId);

        List<UserAvailableTime.Slot> slots = request.availableTimes().stream()
                .map(time -> new UserAvailableTime.Slot(time.dayOfWeek(), time.startTime(), time.endTime()))
                .sorted(Comparator.comparing(UserAvailableTime.Slot::dayOfWeek)
                        .thenComparing(UserAvailableTime.Slot::startTime))
                .toList();
        validateSlots(slots);

        user.replaceAvailableTimes(slots);
        return UserResponses.MyProfile.from(user);
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

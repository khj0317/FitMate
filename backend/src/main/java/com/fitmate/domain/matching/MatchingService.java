package com.fitmate.domain.matching;

import com.fitmate.domain.matching.dto.MatchCandidateResponse;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.SkillLevel;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.domain.user.UserSport;
import com.fitmate.domain.user.dto.UserResponses;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    /** 점수 계산 대상 후보 수 상한. 반경 안 가까운 순으로 이만큼만 점수를 매긴다. */
    static final int CANDIDATE_POOL_SIZE = 200;

    /** 정확한 위치를 역추적(삼각측량)하지 못하도록 거리는 0.5km 단위로 올림해서 보여준다. */
    private static final double DISTANCE_UNIT_KM = 0.5;

    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final MatchCandidateQuery matchCandidateQuery;

    public List<MatchCandidateResponse> recommend(Long userId, Short sportId, Integer radiusKm, int limit) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (me.getActivityLocation() == null) {
            throw new BusinessException(ErrorCode.LOCATION_REQUIRED);
        }

        Map<Short, SkillLevel> myLevels = me.getSports().stream()
                .collect(Collectors.toMap(us -> us.getSport().getId(), UserSport::getSkillLevel));
        Set<Short> targetSportIds = resolveTargetSports(sportId, myLevels);

        double radiusMeters = (radiusKm != null ? radiusKm : me.getSearchRadiusKm()) * 1000.0;
        List<MatchCandidateQuery.Row> rows =
                matchCandidateQuery.find(userId, radiusMeters, targetSportIds, CANDIDATE_POOL_SIZE);

        Map<Long, User> usersById = userRepository.findAllById(rows.stream().map(MatchCandidateQuery.Row::userId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return rows.stream()
                .filter(row -> usersById.containsKey(row.userId())) // 조회 사이에 탈퇴한 사용자 제외
                .map(row -> toCandidate(row, usersById.get(row.userId()), myLevels, targetSportIds, radiusMeters))
                .sorted(Comparator.comparingInt(MatchCandidateResponse::matchScore).reversed()
                        .thenComparingDouble(MatchCandidateResponse::approximateDistanceKm))
                .limit(limit)
                .toList();
    }

    private Set<Short> resolveTargetSports(Short sportId, Map<Short, SkillLevel> myLevels) {
        if (sportId != null) {
            if (!sportRepository.existsById(sportId)) {
                throw new BusinessException(ErrorCode.SPORT_NOT_FOUND);
            }
            return Set.of(sportId);
        }
        if (myLevels.isEmpty()) {
            throw new BusinessException(ErrorCode.SPORT_REQUIRED);
        }
        return myLevels.keySet();
    }

    /** 공통 종목이 여러 개면 실력이 가장 잘 맞는 종목 기준으로 점수를 매긴다. */
    private MatchCandidateResponse toCandidate(MatchCandidateQuery.Row row, User candidate,
                                               Map<Short, SkillLevel> myLevels, Set<Short> targetSportIds,
                                               double radiusMeters) {
        List<MatchCandidateResponse.CommonSport> commonSports = candidate.getSports().stream()
                .filter(us -> targetSportIds.contains(us.getSport().getId()))
                .map(us -> new MatchCandidateResponse.CommonSport(
                        us.getSport().getId(), us.getSport().getName(),
                        myLevels.get(us.getSport().getId()), us.getSkillLevel()))
                .sorted(Comparator.comparingDouble(
                        (MatchCandidateResponse.CommonSport cs) -> MatchScoreCalculator.skillRatio(cs.myLevel(), cs.theirLevel()))
                        .reversed()
                        .thenComparing(MatchCandidateResponse.CommonSport::sportId))
                .toList();

        MatchCandidateResponse.CommonSport best = commonSports.get(0);
        MatchScoreCalculator.Score score = MatchScoreCalculator.calculate(
                row.distanceMeters(), radiusMeters, best.myLevel(), best.theirLevel(),
                row.overlapMinutes(), candidate.getMannerScore());

        return new MatchCandidateResponse(
                candidate.getId(),
                candidate.getNickname(),
                candidate.getProfileImageUrl(),
                candidate.getGender(),
                UserResponses.PublicProfile.ageGroup(candidate.getBirthYear()),
                candidate.getActivityAreaName(),
                candidate.getMannerScore(),
                roundUpDistance(row.distanceMeters()),
                row.overlapMinutes(),
                score.total(),
                MatchCandidateResponse.ScoreDetail.from(score),
                commonSports
        );
    }

    static double roundUpDistance(double meters) {
        double km = meters / 1000.0;
        return Math.max(DISTANCE_UNIT_KM, Math.ceil(km / DISTANCE_UNIT_KM) * DISTANCE_UNIT_KM);
    }
}

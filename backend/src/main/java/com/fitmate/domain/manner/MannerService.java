package com.fitmate.domain.manner;

import com.fitmate.domain.notification.Notification;
import com.fitmate.domain.notification.NotificationService;
import com.fitmate.domain.safety.SafetyService;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MannerService {

    private final MannerReviewRepository reviewRepository;
    private final MannerQuery mannerQuery;
    private final UserRepository userRepository;
    private final SafetyService safetyService;
    private final NotificationService notificationService;

    public List<MannerDtos.Pending> pending(Long userId) {
        return mannerQuery.findPending(userId);
    }

    /**
     * 함께 운동한 상대만 평가할 수 있다 (끝난 모임의 참가자끼리, 또는 수락된 1:1 매칭 상대).
     * 같은 모임·매칭에서 두 번 평가하는 건 부분 유니크 인덱스가 막고,
     * 점수는 UPDATE 한 번으로 더해서 동시에 여러 평가가 들어와도 잃어버리지 않는다.
     */
    @Transactional
    public void review(Long reviewerId, MannerDtos.CreateReview request) {
        if ((request.gatheringId() == null) == (request.matchRequestId() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "모임 또는 매칭 중 하나를 정해 주세요.");
        }
        Set<MannerReview.Tag> tags = request.tags() == null || request.tags().isEmpty()
                ? EnumSet.noneOf(MannerReview.Tag.class) : EnumSet.copyOf(request.tags());
        validateTags(request.rating(), tags);

        Long targetId = request.targetId();
        boolean eligible = !reviewerId.equals(targetId) && (request.gatheringId() != null
                ? mannerQuery.canReviewGathering(reviewerId, targetId, request.gatheringId())
                : mannerQuery.canReviewMatch(reviewerId, targetId, request.matchRequestId()));
        if (!eligible) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (safetyService.isBlockedBetween(reviewerId, targetId)) {
            throw new BusinessException(ErrorCode.USER_UNAVAILABLE);
        }

        MannerReview review = reviewRepository.saveAndFlush(new MannerReview(reviewerId, targetId,
                request.gatheringId(), request.matchRequestId(), request.rating(), tags));
        if (review.getScoreDelta().signum() != 0) {
            userRepository.adjustMannerScore(targetId, review.getScoreDelta());
        }
        if (request.rating() == MannerReview.Rating.GOOD) {
            notificationService.notify(targetId, Notification.Type.MANNER_REVIEW_RECEIVED,
                    "매너 칭찬을 받았어요",
                    "함께 운동한 분이 좋은 평가를 남겼어요. 매너 온도가 올랐어요!",
                    "/profile");
        }
    }

    public MannerDtos.Summary summary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new MannerDtos.Summary(user.getMannerScore(), mannerQuery.countReviews(userId),
                mannerQuery.positiveTagCounts(userId));
    }

    /** 좋았어요엔 칭찬 태그만, 별로였어요엔 아쉬운 태그만 붙일 수 있다 */
    private static void validateTags(MannerReview.Rating rating, Set<MannerReview.Tag> tags) {
        for (MannerReview.Tag tag : tags) {
            boolean allowed = tag.isPositive() ? rating == MannerReview.Rating.GOOD : rating == MannerReview.Rating.BAD;
            if (!allowed) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "평가와 맞지 않는 태그가 있어요.");
            }
        }
    }
}

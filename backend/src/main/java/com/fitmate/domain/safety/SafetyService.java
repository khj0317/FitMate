package com.fitmate.domain.safety;

import com.fitmate.domain.matchrequest.MatchRequestRepository;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 차단·신고. 차단 효과는 각 기능이 existsBetween으로 확인한다.
 * - 추천: 서로 보이지 않음 (MatchCandidateQuery)
 * - 매칭 요청: 보낼 수도 수락할 수도 없음, 차단하는 순간 대기 중인 요청은 취소
 * - 채팅: 메시지를 보낼 수 없음, 차단한 사람의 목록에서는 채팅방이 숨겨짐
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    private final UserBlockRepository blockRepository;
    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MatchRequestRepository matchRequestRepository;

    @Transactional
    public void block(Long blockerId, Long targetId) {
        validateTarget(blockerId, targetId);
        if (!blockRepository.existsById(new UserBlock.Key(blockerId, targetId))) {
            blockRepository.save(new UserBlock(blockerId, targetId));
        }
        matchRequestRepository.cancelPendingBetween(blockerId, targetId, Instant.now());
    }

    @Transactional
    public void unblock(Long blockerId, Long targetId) {
        blockRepository.deleteById(new UserBlock.Key(blockerId, targetId));
    }

    public boolean isBlockedBetween(Long a, Long b) {
        return blockRepository.existsBetween(a, b);
    }

    @Transactional(readOnly = true)
    public List<BlockedUser> getBlocks(Long blockerId) {
        List<UserBlock> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        Map<Long, User> users = userRepository.findAllById(blocks.stream().map(UserBlock::getBlockedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return blocks.stream()
                .filter(block -> users.containsKey(block.getBlockedId()))
                .map(block -> {
                    User user = users.get(block.getBlockedId());
                    return new BlockedUser(user.getId(), user.getNickname(), user.getProfileImageUrl(), block.getCreatedAt());
                })
                .toList();
    }

    /** 검토 대기 중인 같은 신고는 한 번만. 동시에 두 번 눌러도 부분 유니크 인덱스가 막는다 */
    @Transactional
    public void report(Long reporterId, Long targetId, UserReport.ReportReason reason, String detail, boolean alsoBlock) {
        validateTarget(reporterId, targetId);
        if (reportRepository.existsByReporterIdAndReportedIdAndStatus(reporterId, targetId, "PENDING")) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }
        String trimmed = detail == null || detail.isBlank() ? null : detail.strip();
        reportRepository.save(new UserReport(reporterId, targetId, reason, trimmed));
        if (alsoBlock) {
            block(reporterId, targetId);
        }
    }

    private void validateTarget(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!userRepository.existsById(targetId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    public record BlockedUser(Long userId, String nickname, String profileImageUrl, Instant blockedAt) {
    }
}

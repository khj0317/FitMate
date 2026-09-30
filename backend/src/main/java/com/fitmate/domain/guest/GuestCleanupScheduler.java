package com.fitmate.domain.guest;

import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** 1시간마다 만든 지 24시간이 지난 체험 계정을 탈퇴와 똑같이 지운다 (쓴 글·채팅방 참여·매칭 요청도 함께) */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final UserRepository userRepository;
    private final UserService userService;

    @Scheduled(fixedDelayString = "${fitmate.scheduler.guest-cleanup-interval-ms:3600000}",
            initialDelayString = "${fitmate.scheduler.initial-delay-ms:30000}")
    public void run() {
        int deleted = deleteExpired(Instant.now().minus(GuestAccountService.LIFETIME));
        if (deleted > 0) {
            log.info("만료된 체험 계정 {}개를 지웠습니다.", deleted);
        }
    }

    /** before보다 먼저 만든 체험 계정을 지운다. 한 명씩 따로 지워서 하나가 실패해도 나머지는 지운다 */
    int deleteExpired(Instant before) {
        int deleted = 0;
        for (Long id : userRepository.findIdsByRoleAndCreatedAtBefore(User.Role.GUEST, before)) {
            try {
                userService.deleteGuest(id);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("체험 계정 {} 삭제 실패 (다음 주기에 다시 시도)", id, e);
            }
        }
        return deleted;
    }
}

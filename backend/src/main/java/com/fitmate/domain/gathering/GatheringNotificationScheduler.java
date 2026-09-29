package com.fitmate.domain.gathering;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 1분마다 리마인더·평가 요청을 보낼 모임이 있는지 확인한다 (테스트에서는 첫 실행을 늦춰 직접 호출한다) */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatheringNotificationScheduler {

    private final GatheringNotifier notifier;

    @Scheduled(fixedDelayString = "${fitmate.scheduler.gathering-interval-ms:60000}",
            initialDelayString = "${fitmate.scheduler.initial-delay-ms:30000}")
    public void run() {
        try {
            int reminders = notifier.sendReminders();
            int prompts = notifier.sendReviewPrompts();
            if (reminders + prompts > 0) {
                log.info("모임 알림 발송: 리마인더 {}건, 평가 요청 {}건", reminders, prompts);
            }
        } catch (RuntimeException e) {
            log.warn("모임 알림 발송 실패 (다음 주기에 다시 시도)", e);
        }
    }
}

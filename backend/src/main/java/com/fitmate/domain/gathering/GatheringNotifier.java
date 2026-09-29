package com.fitmate.domain.gathering;

import com.fitmate.domain.notification.Notification;
import com.fitmate.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 모임 시작 1시간 전 리마인더와, 모임이 끝난 뒤 매너 평가 요청 알림.
 * 서버가 여러 대여도 한 번만 보내도록 "보냈음" 표시를 조건부 UPDATE로 먼저 차지(claim)한 모임에만 보낸다.
 * 같은 행을 두 서버가 동시에 UPDATE하면 한쪽은 잠금을 기다렸다가 조건(… IS NULL)을 다시 확인해서 0행이 된다.
 */
@Component
@RequiredArgsConstructor
public class GatheringNotifier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    private final JdbcClient jdbcClient;
    private final NotificationService notificationService;

    private record Claimed(Long id, String title, String placeName, OffsetDateTime startsAt) {
    }

    @Transactional
    public int sendReminders() {
        List<Claimed> claimed = claim("""
                UPDATE gatherings SET reminder_sent_at = now()
                WHERE reminder_sent_at IS NULL AND status IN ('RECRUITING', 'CLOSED')
                  AND starts_at > now() AND starts_at <= now() + interval '1 hour'
                RETURNING id, title, place_name, starts_at
                """);
        for (Claimed gathering : claimed) {
            String time = gathering.startsAt().atZoneSameInstant(SEOUL).format(TIME);
            participants(gathering.id()).forEach(userId -> notificationService.notify(userId,
                    Notification.Type.GATHERING_REMINDER,
                    "곧 모임이 시작돼요 ⏰",
                    "'%s' %s · %s".formatted(gathering.title(), time, gathering.placeName()),
                    "/gatherings/" + gathering.id()));
        }
        return claimed.size();
    }

    /** 시작하고 2시간이 지난 모임(3일 이내)에 두 명 이상 있었으면 서로 평가해 달라고 알린다 */
    @Transactional
    public int sendReviewPrompts() {
        List<Claimed> claimed = claim("""
                UPDATE gatherings SET review_prompt_sent_at = now()
                WHERE review_prompt_sent_at IS NULL AND status <> 'CANCELED' AND current_count >= 2
                  AND starts_at <= now() - interval '2 hours' AND starts_at > now() - interval '3 days'
                RETURNING id, title, place_name, starts_at
                """);
        for (Claimed gathering : claimed) {
            participants(gathering.id()).forEach(userId -> notificationService.notify(userId,
                    Notification.Type.REVIEW_REQUESTED,
                    "함께한 메이트를 평가해 주세요",
                    "'%s' 모임은 어떠셨나요? 매너 평가를 남겨 주세요".formatted(gathering.title()),
                    "/gatherings/" + gathering.id()));
        }
        return claimed.size();
    }

    private List<Claimed> claim(String sql) {
        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new Claimed(rs.getLong("id"), rs.getString("title"),
                        rs.getString("place_name"), rs.getObject("starts_at", OffsetDateTime.class)))
                .list();
    }

    private List<Long> participants(Long gatheringId) {
        return jdbcClient.sql("""
                        SELECT user_id FROM gathering_participants WHERE gathering_id = :id AND status = 'JOINED'
                        """)
                .param("id", gatheringId)
                .query(Long.class)
                .list();
    }
}

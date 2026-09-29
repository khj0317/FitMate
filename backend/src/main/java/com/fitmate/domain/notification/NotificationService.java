package com.fitmate.domain.notification;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 알림을 저장하고, 커밋이 끝난 뒤 실시간으로도 보낸다 (NotificationPublisher → Redis → 각 서버 → /user/queue/notifications).
 * 알림을 만든 기능(매칭 요청 등)의 트랜잭션 안에서 호출되므로, 그 기능이 롤백되면 알림도 저장·전송되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int PAGE_SIZE = 30;

    private final NotificationRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcClient jdbcClient;

    /** 받는 사람이 그 종류의 알림을 꺼 두었으면 저장도 전송도 하지 않는다 */
    @Transactional
    public void notify(Long userId, Notification.Type type, String title, String body, String link) {
        if (isMuted(userId, type.category())) {
            return;
        }
        Notification saved = repository.save(new Notification(userId, type, title, body, link));
        eventPublisher.publishEvent(new NotificationCreatedEvent(userId, Item.from(saved)));
    }

    @Transactional(readOnly = true)
    public Page list(Long userId, Long cursor) {
        Limit limit = Limit.of(PAGE_SIZE + 1);
        List<Notification> found = cursor == null
                ? repository.findByUserIdOrderByIdDesc(userId, limit)
                : repository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, limit);
        boolean hasNext = found.size() > PAGE_SIZE;
        List<Notification> page = hasNext ? found.subList(0, PAGE_SIZE) : found;
        return new Page(page.stream().map(Item::from).toList(),
                hasNext ? page.get(page.size() - 1).getId() : null,
                repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND))
                .markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, Instant.now());
    }

    // ---------- 알림 설정 ----------

    @Transactional(readOnly = true)
    public Settings settings(Long userId) {
        String[] muted = jdbcClient.sql("SELECT muted_notification_types FROM users WHERE id = :id")
                .param("id", userId)
                .query((rs, rowNum) -> (String[]) rs.getArray(1).getArray())
                .optional()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Set<Notification.Category> set = EnumSet.noneOf(Notification.Category.class);
        Arrays.stream(muted).forEach(name -> set.add(Notification.Category.valueOf(name)));
        return new Settings(set);
    }

    @Transactional
    public Settings updateSettings(Long userId, Set<Notification.Category> muted) {
        String[] names = (muted == null ? Set.<Notification.Category>of() : muted).stream()
                .map(Enum::name).sorted().toArray(String[]::new);
        jdbcClient.sql("UPDATE users SET muted_notification_types = :muted WHERE id = :id")
                .param("muted", names)
                .param("id", userId)
                .update();
        return settings(userId);
    }

    private boolean isMuted(Long userId, Notification.Category category) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "SELECT :category = ANY(muted_notification_types) FROM users WHERE id = :id")
                .param("category", category.name())
                .param("id", userId)
                .query(Boolean.class)
                .optional()
                .orElse(false));
    }

    /** 꺼 둔 알림 종류 */
    public record Settings(Set<Notification.Category> muted) {
    }

    public record Item(Long id, Notification.Type type, String title, String body, String link, boolean read,
                       Instant createdAt) {
        static Item from(Notification notification) {
            return new Item(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getBody(), notification.getLink(), notification.getReadAt() != null,
                    notification.getCreatedAt());
        }
    }

    public record Page(List<Item> items, Long nextCursor, long unreadCount) {
    }

    public record NotificationCreatedEvent(Long userId, Item item) {
    }
}

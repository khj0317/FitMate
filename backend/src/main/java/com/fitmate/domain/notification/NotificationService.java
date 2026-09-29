package com.fitmate.domain.notification;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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

    @Transactional
    public void notify(Long userId, Notification.Type type, String title, String body, String link) {
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

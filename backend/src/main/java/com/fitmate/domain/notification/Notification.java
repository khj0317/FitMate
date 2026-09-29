package com.fitmate.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 300)
    private String body;

    /** 알림을 누르면 이동할 웹 경로 (예: /chats/3) */
    @Column(length = 200)
    private String link;

    private Instant readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(Long userId, Type type, String title, String body, String link) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    /** 사용자가 알림 설정에서 켜고 끄는 단위 */
    public enum Category {
        MATCH, GATHERING, MANNER, COMMUNITY
    }

    public enum Type {
        MATCH_REQUEST_RECEIVED(Category.MATCH),
        MATCH_REQUEST_ACCEPTED(Category.MATCH),
        GATHERING_JOINED(Category.GATHERING),
        GATHERING_CANCELED(Category.GATHERING),
        GATHERING_REMINDER(Category.GATHERING),
        MANNER_REVIEW_RECEIVED(Category.MANNER),
        REVIEW_REQUESTED(Category.MANNER),
        POST_COMMENTED(Category.COMMUNITY),
        COMMENT_REPLIED(Category.COMMUNITY);

        private final Category category;

        Type(Category category) {
            this.category = category;
        }

        public Category category() {
            return category;
        }
    }
}

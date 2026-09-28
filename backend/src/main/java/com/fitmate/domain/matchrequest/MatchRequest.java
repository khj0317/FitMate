package com.fitmate.domain.matchrequest;

import com.fitmate.domain.chat.ChatRoom;
import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.user.User;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "match_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchRequestStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    public MatchRequest(User requester, User receiver, Sport sport, String message) {
        this.requester = requester;
        this.receiver = receiver;
        this.sport = sport;
        this.message = message;
        this.status = MatchRequestStatus.PENDING;
    }

    public void accept(ChatRoom chatRoom) {
        changeStatus(MatchRequestStatus.ACCEPTED);
        this.chatRoom = chatRoom;
    }

    public void reject() {
        changeStatus(MatchRequestStatus.REJECTED);
    }

    public void cancel() {
        changeStatus(MatchRequestStatus.CANCELED);
    }

    public boolean isRequester(Long userId) {
        return requester.getId().equals(userId);
    }

    public boolean isReceiver(Long userId) {
        return receiver.getId().equals(userId);
    }

    /** 대기 중인 요청만 처리할 수 있다. 이미 수락/거절/취소된 요청을 다시 처리하면 409. */
    private void changeStatus(MatchRequestStatus next) {
        if (status != MatchRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_MATCH_REQUEST_STATUS);
        }
        this.status = next;
        this.respondedAt = Instant.now();
    }
}

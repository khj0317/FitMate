package com.fitmate.domain.matchrequest.dto;

import com.fitmate.domain.matchrequest.MatchRequest;
import com.fitmate.domain.matchrequest.MatchRequestStatus;
import com.fitmate.domain.user.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class MatchRequestDtos {

    private MatchRequestDtos() {
    }

    public record Create(
            @NotNull Long receiverId,
            @NotNull Short sportId,
            @Size(max = 300, message = "메시지는 300자 이하여야 합니다.") String message
    ) {
    }

    public record Created(Long matchRequestId) {
    }

    public record Accepted(Long matchRequestId, Long chatRoomId) {
    }

    public record Response(
            Long id,
            MatchRequestStatus status,
            Short sportId,
            String sportName,
            String message,
            Counterpart counterpart,
            Instant createdAt,
            Instant respondedAt,
            Long chatRoomId
    ) {
        /** counterpart는 받은 요청이면 보낸 사람, 보낸 요청이면 받은 사람 */
        public static Response of(MatchRequest request, User counterpart) {
            return new Response(
                    request.getId(),
                    request.getStatus(),
                    request.getSport().getId(),
                    request.getSport().getName(),
                    request.getMessage(),
                    Counterpart.from(counterpart),
                    request.getCreatedAt(),
                    request.getRespondedAt(),
                    request.getChatRoom() == null ? null : request.getChatRoom().getId()
            );
        }
    }

    public record Counterpart(Long userId, String nickname, String profileImageUrl, BigDecimal mannerScore) {
        static Counterpart from(User user) {
            return new Counterpart(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getMannerScore());
        }
    }
}

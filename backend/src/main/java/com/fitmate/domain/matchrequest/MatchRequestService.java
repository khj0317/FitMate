package com.fitmate.domain.matchrequest;

import com.fitmate.domain.chat.ChatRoom;
import com.fitmate.domain.chat.ChatRoomRepository;
import com.fitmate.domain.matchrequest.dto.MatchRequestDtos;
import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchRequestService {

    private final MatchRequestRepository matchRequestRepository;
    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final ChatRoomRepository chatRoomRepository;

    /**
     * 대기 중 요청 중복은 사전 검사로 친절한 에러를 주고,
     * 동시 요청은 부분 유니크 인덱스(uq_match_requests_pending)가 최종적으로 막는다.
     */
    @Transactional
    public MatchRequestDtos.Created create(Long requesterId, MatchRequestDtos.Create request) {
        if (requesterId.equals(request.receiverId())) {
            throw new BusinessException(ErrorCode.CANNOT_REQUEST_SELF);
        }
        User requester = getUser(requesterId);
        User receiver = getUser(request.receiverId());
        Sport sport = sportRepository.findById(request.sportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SPORT_NOT_FOUND));

        boolean receiverPlaysSport = receiver.getSports().stream()
                .anyMatch(us -> us.getSport().getId().equals(sport.getId()));
        if (!receiverPlaysSport) {
            throw new BusinessException(ErrorCode.RECEIVER_DOES_NOT_PLAY_SPORT);
        }
        if (chatRoomRepository.existsByDirectKey(ChatRoom.directKey(requesterId, receiver.getId()))) {
            throw new BusinessException(ErrorCode.ALREADY_MATCHED);
        }
        if (matchRequestRepository.existsByRequesterIdAndReceiverIdAndStatus(
                receiver.getId(), requesterId, MatchRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.REVERSE_MATCH_REQUEST_EXISTS);
        }
        if (matchRequestRepository.existsByRequesterIdAndReceiverIdAndStatus(
                requesterId, receiver.getId(), MatchRequestStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_MATCH_REQUEST);
        }

        String message = request.message() == null || request.message().isBlank() ? null : request.message().trim();
        MatchRequest saved = matchRequestRepository.save(new MatchRequest(requester, receiver, sport, message));
        return new MatchRequestDtos.Created(saved.getId());
    }

    public List<MatchRequestDtos.Response> getReceived(Long userId, MatchRequestStatus status) {
        return matchRequestRepository.findByReceiverIdAndStatusOrderByIdDesc(userId, status).stream()
                .map(request -> MatchRequestDtos.Response.of(request, request.getRequester()))
                .toList();
    }

    public List<MatchRequestDtos.Response> getSent(Long userId, MatchRequestStatus status) {
        return matchRequestRepository.findByRequesterIdAndStatusOrderByIdDesc(userId, status).stream()
                .map(request -> MatchRequestDtos.Response.of(request, request.getReceiver()))
                .toList();
    }

    /** 수락하면 두 사람의 1:1 채팅방을 만든다. 이미 있으면 그 방을 재사용한다. */
    @Transactional
    public MatchRequestDtos.Accepted accept(Long userId, Long requestId) {
        MatchRequest request = lockForReceiver(userId, requestId);
        User requester = request.getRequester();
        User receiver = request.getReceiver();

        ChatRoom room = chatRoomRepository.findByDirectKey(ChatRoom.directKey(requester.getId(), receiver.getId()))
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.direct(requester, receiver)));
        request.accept(room);
        return new MatchRequestDtos.Accepted(request.getId(), room.getId());
    }

    @Transactional
    public void reject(Long userId, Long requestId) {
        lockForReceiver(userId, requestId).reject();
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        MatchRequest request = matchRequestRepository.findByIdForUpdate(requestId)
                .filter(found -> found.isRequester(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
        request.cancel();
    }

    /** 다른 사람의 요청은 존재 여부도 알 수 없도록 404로 처리한다. */
    private MatchRequest lockForReceiver(Long userId, Long requestId) {
        return matchRequestRepository.findByIdForUpdate(requestId)
                .filter(found -> found.isReceiver(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_REQUEST_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

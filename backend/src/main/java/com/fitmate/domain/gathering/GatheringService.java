package com.fitmate.domain.gathering;

import com.fitmate.domain.chat.ChatService;
import com.fitmate.domain.gathering.dto.GatheringDtos;
import com.fitmate.domain.notification.Notification;
import com.fitmate.domain.notification.NotificationService;
import com.fitmate.domain.safety.SafetyService;
import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.util.GeoPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringService {

    static final Duration MIN_LEAD_TIME = Duration.ofMinutes(10);
    static final Duration MAX_LEAD_TIME = Duration.ofDays(60);

    private final GatheringRepository gatheringRepository;
    private final GatheringParticipantRepository participantRepository;
    private final GatheringQuery gatheringQuery;
    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final ChatService chatService;
    private final SafetyService safetyService;
    private final NotificationService notificationService;
    private final Clock clock;

    /** 모임을 만들면 모임장이 첫 참가자가 되고, 단체 채팅방이 함께 만들어진다 */
    @Transactional
    public GatheringDtos.Detail create(Long hostId, GatheringDtos.Create request) {
        Instant now = clock.instant();
        if (request.startsAt().isBefore(now.plus(MIN_LEAD_TIME)) || request.startsAt().isAfter(now.plus(MAX_LEAD_TIME))) {
            throw new BusinessException(ErrorCode.INVALID_GATHERING_TIME);
        }
        User host = getUser(hostId);
        Sport sport = sportRepository.findById(request.sportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SPORT_NOT_FOUND));
        String description = request.description() == null || request.description().isBlank() ? null : request.description().strip();

        Gathering gathering = gatheringRepository.save(new Gathering(host, sport, request.title().strip(), description,
                GeoPoints.of(request.location().latitude(), request.location().longitude()),
                request.placeName().strip(), request.startsAt(), request.capacity()));
        participantRepository.save(new GatheringParticipant(gathering.getId(), hostId));
        chatService.createGatheringRoom(gathering.getId(), host);
        return detail(hostId, gathering.getId());
    }

    public List<GatheringDtos.Summary> nearby(Long userId, Short sportId, Integer radiusKm) {
        User me = getUser(userId);
        if (me.getActivityLocation() == null) {
            throw new BusinessException(ErrorCode.LOCATION_REQUIRED);
        }
        double radiusMeters = (radiusKm != null ? radiusKm : me.getSearchRadiusKm()) * 1000.0;
        return gatheringQuery.findNearby(userId, radiusMeters, sportId);
    }

    public List<GatheringDtos.Summary> mine(Long userId) {
        return gatheringQuery.findMine(userId);
    }

    public GatheringDtos.Detail detail(Long userId, Long gatheringId) {
        GatheringDtos.Summary summary = gatheringQuery.findOne(userId, gatheringId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));
        if (safetyService.isBlockedBetween(userId, summary.host().userId())) {
            throw new BusinessException(ErrorCode.GATHERING_NOT_FOUND);
        }
        Gathering gathering = getGathering(gatheringId);
        return new GatheringDtos.Detail(
                summary,
                gathering.getDescription(),
                gathering.getLocation().getY(),
                gathering.getLocation().getX(),
                gatheringQuery.findParticipants(gatheringId),
                gathering.isHost(userId),
                summary.joined() ? chatService.gatheringRoomId(gatheringId) : null);
    }

    /**
     * 선착순 참여.
     * 1) 이미 참여 중이면 거절
     * 2) 조건부 UPDATE로 자리를 확보 (정원 초과·마감·시작됨이면 0행 → 거절)
     * 3) 자리 확보로 모임 행이 잠긴 상태에서 참가 기록을 다시 확인하고, 만들거나(처음) 되살린다(나갔다가 다시)
     * 같은 사람이 동시에 여러 번 눌러도 2)의 행 잠금 때문에 차례로 처리되고, 3)에서 두 번째부터 거절되며 자리도 돌려준다.
     */
    @Transactional
    public GatheringDtos.Joined join(Long userId, Long gatheringId) {
        Gathering gathering = getGathering(gatheringId);
        if (safetyService.isBlockedBetween(userId, gathering.getHost().getId())) {
            throw new BusinessException(ErrorCode.GATHERING_NOT_FOUND); // 상세 조회와 같이 차단 관계면 없는 모임처럼 보인다
        }
        GatheringParticipant existing = participantRepository.findById(new GatheringParticipant.Key(gatheringId, userId))
                .orElse(null);
        if (existing != null && existing.isJoined()) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        if (gatheringRepository.tryReserveSeat(gatheringId) == 0) {
            throw new BusinessException(gathering.hasStarted(clock.instant())
                    ? ErrorCode.GATHERING_ALREADY_STARTED : ErrorCode.GATHERING_FULL);
        }
        // 자리를 확보한 지금은 모임 행 잠금을 쥐고 있어서, 같은 사람의 동시 요청은 여기서 한 줄로 선다.
        // 앞선 요청이 커밋한 참가 기록이 보이도록 다시 읽어서 확인한다 (예외가 나면 자리 확보도 롤백된다)
        existing = participantRepository.findById(new GatheringParticipant.Key(gatheringId, userId)).orElse(null);
        if (existing != null && existing.isJoined()) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        if (existing != null) {
            existing.rejoin();
        } else {
            participantRepository.saveAndFlush(new GatheringParticipant(gatheringId, userId));
        }

        User user = getUser(userId);
        Long roomId = chatService.joinGatheringRoom(gatheringId, user);
        notificationService.notify(gathering.getHost().getId(), Notification.Type.GATHERING_JOINED,
                "새 참가자가 들어왔어요",
                "%s님이 '%s' 모임에 참여했어요".formatted(user.getNickname(), gathering.getTitle()),
                "/gatherings/" + gatheringId);
        return new GatheringDtos.Joined(gatheringId, roomId);
    }

    /** 참가자가 나가면 자리를 돌려주고 단체 채팅방에서도 나간다. 모임장은 나갈 수 없고 모임을 취소해야 한다 */
    @Transactional
    public void leave(Long userId, Long gatheringId) {
        Gathering gathering = getGathering(gatheringId);
        if (gathering.isHost(userId)) {
            throw new BusinessException(ErrorCode.HOST_CANNOT_LEAVE);
        }
        if (gathering.hasStarted(clock.instant())) {
            throw new BusinessException(ErrorCode.GATHERING_ALREADY_STARTED);
        }
        // 자리를 먼저 돌려놓아 모임 행을 잠근 뒤 참가 기록을 확인한다. 동시에 두 번 나가도 두 번째는 NOT_JOINED로 롤백된다
        gatheringRepository.releaseSeat(gatheringId);
        GatheringParticipant participant = participantRepository.findById(new GatheringParticipant.Key(gatheringId, userId))
                .filter(GatheringParticipant::isJoined)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        participant.leave();
        chatService.leaveGatheringRoom(gatheringId, userId);
    }

    /** 모임장이 시작 전에 모임을 취소하면 참가자 모두에게 알린다 */
    @Transactional
    public void cancel(Long userId, Long gatheringId) {
        Gathering gathering = getGathering(gatheringId);
        if (!gathering.isHost(userId)) {
            throw new BusinessException(ErrorCode.NOT_GATHERING_HOST);
        }
        gathering.cancel(clock.instant());
        participantRepository.findByGatheringIdAndStatus(gatheringId, GatheringParticipant.Status.JOINED).stream()
                .filter(participant -> !participant.getUserId().equals(userId))
                .forEach(participant -> notificationService.notify(participant.getUserId(),
                        Notification.Type.GATHERING_CANCELED,
                        "모임이 취소됐어요",
                        "'%s' 모임이 모임장 사정으로 취소됐어요".formatted(gathering.getTitle()),
                        "/gatherings/" + gatheringId));
    }

    private Gathering getGathering(Long gatheringId) {
        return gatheringRepository.findById(gatheringId)
                .filter(gathering -> gathering.getStatus() != Gathering.Status.CANCELED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GATHERING_NOT_FOUND));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

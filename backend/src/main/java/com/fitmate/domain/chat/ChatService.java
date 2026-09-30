package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import com.fitmate.domain.safety.SafetyService;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.image.ImagePurpose;
import com.fitmate.global.image.ImageUploader;
import com.fitmate.global.image.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomMemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatRoomQuery chatRoomQuery;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUploader imageUploader;
    private final SafetyService safetyService;

    /**
     * 메시지를 저장하고, 커밋이 끝난 뒤 Redis로 발행한다(ChatMessagePublisher).
     * 커밋 전에 발행하면 롤백된 메시지가 전송되거나, 받는 쪽이 아직 DB에 없는 메시지를 조회할 수 있다.
     */
    @Transactional
    public ChatDtos.Message send(Long roomId, Long senderId, String content) {
        ChatRoomMember sender = getMember(roomId, senderId);
        ensureCanSend(sender);
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty() || trimmed.length() > ChatMessage.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return publish(sender, messageRepository.save(new ChatMessage(roomId, senderId, trimmed)));
    }

    /** 사진은 검증·가공(메타데이터 제거, 크기 축소) 후 저장소에 올리고 사진 메시지로 보낸다. */
    @Transactional
    public ChatDtos.Message sendImage(Long roomId, Long senderId, MultipartFile file) {
        ChatRoomMember sender = getMember(roomId, senderId);
        ensureCanSend(sender); // 사진을 올리기 전에 확인해서 보낼 수 없는 방에 파일이 쌓이지 않게 한다
        if (userRepository.existsByIdAndRole(senderId, User.Role.GUEST)) {
            throw new BusinessException(ErrorCode.GUEST_RESTRICTED); // 누구나 만들 수 있는 체험 계정으로는 파일을 올릴 수 없다
        }
        StoredImage image = imageUploader.upload(file, ImagePurpose.CHAT);
        return publish(sender, messageRepository.save(
                ChatMessage.image(roomId, senderId, image.url(), image.width(), image.height())));
    }

    /** 1:1 방에서 상대가 탈퇴했거나 차단 관계면 보낼 수 없다 */
    private void ensureCanSend(ChatRoomMember sender) {
        if (sender.getRoom().getType() != ChatRoomType.DIRECT) {
            return;
        }
        Long me = sender.getId().getUserId();
        Long other = memberRepository.findByIdRoomId(sender.getId().getRoomId()).stream()
                .map(member -> member.getId().getUserId())
                .filter(userId -> !userId.equals(me))
                .findFirst()
                .orElse(null);
        if (other == null || safetyService.isBlockedBetween(me, other)) {
            throw new BusinessException(ErrorCode.CHAT_UNAVAILABLE);
        }
    }

    private ChatDtos.Message publish(ChatRoomMember sender, ChatMessage saved) {
        ChatDtos.Message message = ChatDtos.Message.of(saved, sender.getUser().getNickname());
        eventPublisher.publishEvent(new ChatMessageSavedEvent(message));
        // 메시지를 보냈다는 건 그 전 메시지를 모두 읽었다는 뜻 (카카오톡처럼 답장하면 상대 화면의 1이 사라진다)
        advanceRead(sender, saved.getId());
        return message;
    }

    public ChatDtos.MessagePage getMessages(Long roomId, Long userId, Long cursor, int size) {
        getMember(roomId, userId);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        // 한 개 더 조회해서 다음 페이지가 있는지 판단한다
        Limit limit = Limit.of(pageSize + 1);
        List<ChatMessage> found = cursor == null
                ? messageRepository.findByRoomIdOrderByIdDesc(roomId, limit)
                : messageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, limit);

        boolean hasNext = found.size() > pageSize;
        List<ChatMessage> page = hasNext ? found.subList(0, pageSize) : found;
        Map<Long, String> nicknames = nicknames(page.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet()));

        List<ChatDtos.Message> messages = page.stream()
                .map(message -> ChatDtos.Message.of(message, nicknames.get(message.getSenderId())))
                .toList();
        return new ChatDtos.MessagePage(messages, hasNext ? page.get(page.size() - 1).getId() : null,
                otherLastReadMessageId(roomId, userId), readCursors(roomId, userId));
    }

    /** 나를 뺀 멤버들의 읽음 위치. 메시지마다 "안 읽은 사람 수"를 계산하는 데 쓴다 (단체방) */
    private List<ChatDtos.ReadCursor> readCursors(Long roomId, Long userId) {
        return memberRepository.findByIdRoomId(roomId).stream()
                .filter(member -> !member.getId().getUserId().equals(userId))
                .map(member -> new ChatDtos.ReadCursor(member.getId().getUserId(), member.getLastReadMessageId()))
                .toList();
    }

    /** 1:1 방에서 상대가 어디까지 읽었는지. 이 ID보다 큰 내 메시지에 "1"을 표시한다 */
    private Long otherLastReadMessageId(Long roomId, Long userId) {
        return memberRepository.findByIdRoomId(roomId).stream()
                .filter(member -> !member.getId().getUserId().equals(userId))
                .map(ChatRoomMember::getLastReadMessageId)
                .filter(Objects::nonNull)
                .min(Long::compare)
                .orElse(null);
    }

    @Transactional
    public void markRead(Long roomId, Long userId, Long lastMessageId) {
        ChatRoomMember member = getMember(roomId, userId);
        if (!messageRepository.existsByIdAndRoomId(lastMessageId, roomId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        advanceRead(member, lastMessageId);
    }

    /** 읽음 위치가 실제로 앞으로 이동했을 때만 알린다 (같은 위치를 여러 번 보내도 알림은 한 번) */
    private void advanceRead(ChatRoomMember member, Long messageId) {
        if (member.markRead(messageId)) {
            eventPublisher.publishEvent(new ChatReadEvent(
                    member.getId().getRoomId(), member.getId().getUserId(), messageId));
        }
    }

    public List<ChatDtos.Room> getRooms(Long userId) {
        return chatRoomQuery.findRooms(userId);
    }

    // ---------- 모임 단체 채팅방 (GatheringService에서 사용) ----------

    @Transactional
    public Long createGatheringRoom(Long gatheringId, User host) {
        return chatRoomRepository.save(ChatRoom.gathering(gatheringId, host)).getId();
    }

    /** 모임에 참여하면 단체 채팅방에 들어간다. 이미 멤버면 그대로 둔다 */
    @Transactional
    public Long joinGatheringRoom(Long gatheringId, User user) {
        ChatRoom room = gatheringRoom(gatheringId);
        ChatRoomMember.Id id = new ChatRoomMember.Id(room.getId(), user.getId());
        if (!memberRepository.existsById(id)) {
            // 들어온 안내를 먼저 남기고, 새 멤버는 그 안내까지 읽은 것으로 시작한다 (이전 대화는 안 읽은 수에 세지 않음)
            ChatMessage joined = messageRepository.save(
                    ChatMessage.system(room.getId(), user.getNickname() + "님이 들어왔어요"));
            memberRepository.save(new ChatRoomMember(room, user, joined.getId()));
            publishSystem(joined);
        }
        return room.getId();
    }

    /**
     * 나가면 멤버에서 빠지고 "OO님이 나갔어요" 안내를 보낸다.
     * 채팅방을 보고 있는 사람들은 이 안내를 받으면 읽음 위치를 다시 불러와서, 나간 사람을 "안 읽은 사람 수"에서 뺀다
     */
    @Transactional
    public void leaveGatheringRoom(Long gatheringId, Long userId) {
        ChatRoom room = gatheringRoom(gatheringId);
        ChatRoomMember.Id id = new ChatRoomMember.Id(room.getId(), userId);
        if (!memberRepository.existsById(id)) {
            return;
        }
        memberRepository.deleteById(id);
        String nickname = userRepository.findById(userId).map(User::getNickname).orElse("참여자");
        publishSystem(messageRepository.save(ChatMessage.system(room.getId(), nickname + "님이 나갔어요")));
    }

    private void publishSystem(ChatMessage saved) {
        eventPublisher.publishEvent(new ChatMessageSavedEvent(ChatDtos.Message.of(saved, null)));
    }

    public Long gatheringRoomId(Long gatheringId) {
        return chatRoomRepository.findByGatheringId(gatheringId).map(ChatRoom::getId).orElse(null);
    }

    private ChatRoom gatheringRoom(Long gatheringId) {
        return chatRoomRepository.findByGatheringId(gatheringId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    public boolean isMember(Long roomId, Long userId) {
        return memberRepository.existsById(new ChatRoomMember.Id(roomId, userId));
    }

    /** 참여하지 않은 방은 존재 여부도 알 수 없도록 404로 처리한다. */
    private ChatRoomMember getMember(Long roomId, Long userId) {
        return memberRepository.findById(new ChatRoomMember.Id(roomId, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private Map<Long, String> nicknames(Collection<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }
}

package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ChatMessageRepository messageRepository;
    private final ChatRoomQuery chatRoomQuery;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 메시지를 저장하고, 커밋이 끝난 뒤 Redis로 발행한다(ChatMessagePublisher).
     * 커밋 전에 발행하면 롤백된 메시지가 전송되거나, 받는 쪽이 아직 DB에 없는 메시지를 조회할 수 있다.
     */
    @Transactional
    public ChatDtos.Message send(Long roomId, Long senderId, String content) {
        ChatRoomMember sender = getMember(roomId, senderId);
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.isEmpty() || trimmed.length() > ChatMessage.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        ChatMessage saved = messageRepository.save(new ChatMessage(roomId, senderId, trimmed));
        sender.markRead(saved.getId()); // 내가 보낸 메시지는 읽은 것으로 처리

        ChatDtos.Message message = ChatDtos.Message.of(saved, sender.getUser().getNickname());
        eventPublisher.publishEvent(new ChatMessageSavedEvent(message));
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
        return new ChatDtos.MessagePage(messages, hasNext ? page.get(page.size() - 1).getId() : null);
    }

    @Transactional
    public void markRead(Long roomId, Long userId, Long lastMessageId) {
        ChatRoomMember member = getMember(roomId, userId);
        if (!messageRepository.existsByIdAndRoomId(lastMessageId, roomId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        member.markRead(lastMessageId);
    }

    public List<ChatDtos.Room> getRooms(Long userId) {
        return chatRoomQuery.findRooms(userId);
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

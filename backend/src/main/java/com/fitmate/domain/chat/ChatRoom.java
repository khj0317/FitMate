package com.fitmate.domain.chat;

import com.fitmate.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomType type;

    private Long gatheringId;

    @Column(length = 50, unique = true)
    private String directKey;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoomMember> members = new ArrayList<>();

    public static ChatRoom direct(User a, User b) {
        ChatRoom room = new ChatRoom();
        room.type = ChatRoomType.DIRECT;
        room.directKey = directKey(a.getId(), b.getId());
        room.members.add(new ChatRoomMember(room, a));
        room.members.add(new ChatRoomMember(room, b));
        return room;
    }

    /** 요청 방향과 상관없이 같은 두 사람이면 같은 키가 나오도록 작은 ID를 앞에 둔다. */
    public static String directKey(Long userId1, Long userId2) {
        return Math.min(userId1, userId2) + ":" + Math.max(userId1, userId2);
    }
}

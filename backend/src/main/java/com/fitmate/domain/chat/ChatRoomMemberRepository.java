package com.fitmate.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.Id> {
}

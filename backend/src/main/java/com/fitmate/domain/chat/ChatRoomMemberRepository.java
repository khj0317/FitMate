package com.fitmate.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.Id> {

    List<ChatRoomMember> findByIdRoomId(Long roomId);
}

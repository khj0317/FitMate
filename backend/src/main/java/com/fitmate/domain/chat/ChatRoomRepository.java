package com.fitmate.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByDirectKey(String directKey);

    boolean existsByDirectKey(String directKey);
}

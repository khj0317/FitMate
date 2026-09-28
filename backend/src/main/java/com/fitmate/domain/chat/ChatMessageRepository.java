package com.fitmate.domain.chat;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최신 메시지부터 (첫 페이지) */
    List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Limit limit);

    /** cursor보다 오래된 메시지 (다음 페이지). OFFSET 없이 인덱스를 타므로 대화가 길어져도 일정한 속도 */
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Limit limit);

    boolean existsByIdAndRoomId(Long id, Long roomId);
}

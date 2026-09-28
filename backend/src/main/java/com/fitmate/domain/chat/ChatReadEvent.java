package com.fitmate.domain.chat;

/** 누군가 채팅방을 어디까지 읽었는지. 상대 화면의 "1" 표시를 지우는 데 쓴다. */
public record ChatReadEvent(Long roomId, Long userId, Long lastReadMessageId) {
}

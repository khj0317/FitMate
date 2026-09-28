package com.fitmate.domain.chat;

import com.fitmate.domain.chat.dto.ChatDtos;

public record ChatMessageSavedEvent(ChatDtos.Message message) {
}

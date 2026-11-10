package com.example.demo.dto.response;

import com.example.demo.entity.DirectMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record DirectMessageResponse(
		UUID id,
		UUID conversationId,
		UUID senderId,
		String senderName,
		String content,
		LocalDateTime createdAt) {

	public static DirectMessageResponse from(DirectMessage message) {
		return new DirectMessageResponse(
				message.getId(),
				message.getConversation().getId(),
				message.getSender().getId(),
				message.getSender().getFullName(),
				message.getContent(),
				message.getCreatedAt());
	}
}

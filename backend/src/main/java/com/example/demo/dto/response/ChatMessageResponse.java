package com.example.demo.dto.response;

import com.example.demo.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChatMessageResponse(
		UUID id,
		UUID projectId,
		UUID senderId,
		String senderName,
		String content,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime editedAt,
		LocalDateTime deletedAt,
		boolean deleted) {

	public static ChatMessageResponse from(ChatMessage message) {
		boolean deleted = message.getDeletedAt() != null;
		return new ChatMessageResponse(
				message.getId(),
				message.getProject().getId(),
				message.getSender().getId(),
				message.getSender().getFullName(),
				deleted ? null : message.getContent(),
				message.getCreatedAt(),
				message.getUpdatedAt(),
				message.getEditedAt(),
				message.getDeletedAt(),
				deleted);
	}
}

package com.example.demo.dto.response;

import com.example.demo.entity.DirectConversation;
import java.time.LocalDateTime;
import java.util.UUID;

public record DirectConversationResponse(
		UUID id,
		UUID workspaceId,
		UUID recipientId,
		String recipientName,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static DirectConversationResponse from(DirectConversation conversation, UUID currentUserId) {
		var recipient = conversation.otherParticipant(currentUserId);
		return new DirectConversationResponse(
				conversation.getId(),
				conversation.getWorkspace().getId(),
				recipient.getId(),
				recipient.getFullName(),
				conversation.getCreatedAt(),
				conversation.getUpdatedAt());
	}
}

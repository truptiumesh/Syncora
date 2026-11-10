package com.example.demo.dto.response;

import com.example.demo.entity.Notification;
import com.example.demo.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
		UUID id,
		NotificationType type,
		String title,
		String message,
		UUID actorId,
		String actorName,
		UUID workspaceId,
		String workspaceName,
		UUID projectId,
		String projectName,
		UUID taskId,
		String taskKey,
		String taskTitle,
		UUID taskCommentId,
		UUID chatMessageId,
		boolean read,
		LocalDateTime createdAt,
		LocalDateTime readAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getMessage(),
				notification.getActor() == null ? null : notification.getActor().getId(),
				notification.getActor() == null ? "Former member" : notification.getActor().getFullName(),
				notification.getWorkspace() == null ? null : notification.getWorkspace().getId(),
				notification.getWorkspace() == null ? null : notification.getWorkspace().getName(),
				notification.getProject() == null ? null : notification.getProject().getId(),
				notification.getProject() == null ? null : notification.getProject().getName(),
				notification.getTask() == null ? null : notification.getTask().getId(),
				notification.getTask() == null ? null : notification.getTask().getTaskKey(),
				notification.getTask() == null ? null : notification.getTask().getTitle(),
				notification.getTaskComment() == null ? null : notification.getTaskComment().getId(),
				notification.getChatMessage() == null ? null : notification.getChatMessage().getId(),
				notification.isRead(),
				notification.getCreatedAt(),
				notification.getReadAt());
	}
}

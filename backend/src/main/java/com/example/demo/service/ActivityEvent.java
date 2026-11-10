package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
import java.util.List;
import java.util.UUID;

public record ActivityEvent(
		UUID eventId,
		UUID actorId,
		String actorName,
		ActivityType activityType,
		String description,
		UUID workspaceId,
		UUID projectId,
		UUID taskId,
		UUID taskCommentId,
		UUID chatMessageId,
		List<NotificationTarget> notifications) {

	public ActivityEvent {
		notifications = List.copyOf(notifications);
	}

	public static ActivityEvent create(
			User actor,
			ActivityType activityType,
			String description,
			UUID workspaceId,
			UUID projectId,
			UUID taskId,
			UUID taskCommentId,
			UUID chatMessageId,
			List<NotificationTarget> notifications) {
		return new ActivityEvent(
				UUID.randomUUID(),
				actor.getId(),
				actor.getFullName(),
				activityType,
				description,
				workspaceId,
				projectId,
				taskId,
				taskCommentId,
				chatMessageId,
				notifications);
	}

	public record NotificationTarget(
			UUID recipientId,
			NotificationType type,
			String title,
			String message) {
	}
}

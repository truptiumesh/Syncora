package com.example.demo.dto.response;

import com.example.demo.entity.ActivityRecord;
import com.example.demo.enums.ActivityType;
import java.time.LocalDateTime;
import java.util.UUID;

public record ActivityResponse(
		UUID id,
		ActivityType type,
		String description,
		UUID actorId,
		String actorName,
		UUID workspaceId,
		UUID projectId,
		String projectName,
		UUID taskId,
		String taskKey,
		String taskTitle,
		LocalDateTime createdAt) {

	public static ActivityResponse from(ActivityRecord activity) {
		return new ActivityResponse(
				activity.getId(),
				activity.getType(),
				activity.getDescription(),
				activity.getActor() == null ? null : activity.getActor().getId(),
				activity.getActor() == null ? "Former member" : activity.getActor().getFullName(),
				activity.getWorkspace().getId(),
				activity.getProject() == null ? null : activity.getProject().getId(),
				activity.getProject() == null ? null : activity.getProject().getName(),
				activity.getTask() == null ? null : activity.getTask().getId(),
				activity.getTask() == null ? null : activity.getTask().getTaskKey(),
				activity.getTask() == null ? null : activity.getTask().getTitle(),
				activity.getCreatedAt());
	}
}

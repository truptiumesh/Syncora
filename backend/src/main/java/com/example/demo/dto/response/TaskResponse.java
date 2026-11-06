package com.example.demo.dto.response;

import com.example.demo.entity.Task;
import com.example.demo.enums.TaskPriority;
import com.example.demo.enums.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
		UUID id,
		UUID projectId,
		String title,
		String description,
		String taskKey,
		TaskStatus status,
		TaskPriority priority,
		UUID assigneeId,
		String assigneeName,
		UUID createdBy,
		String creatorName,
		LocalDate dueDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime completedAt,
		long commentCount) {

	public static TaskResponse from(Task task, long commentCount) {
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
				task.getTitle(),
				task.getDescription(),
				task.getTaskKey(),
				task.getStatus(),
				task.getPriority(),
				task.getAssignee() == null ? null : task.getAssignee().getId(),
				task.getAssignee() == null ? null : task.getAssignee().getFullName(),
				task.getCreatedBy().getId(),
				task.getCreatedBy().getFullName(),
				task.getDueDate(),
				task.getCreatedAt(),
				task.getUpdatedAt(),
				task.getCompletedAt(),
				commentCount);
	}
}

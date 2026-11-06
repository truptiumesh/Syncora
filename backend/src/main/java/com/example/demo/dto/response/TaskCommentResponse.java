package com.example.demo.dto.response;

import com.example.demo.entity.TaskComment;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaskCommentResponse(
		UUID id,
		UUID taskId,
		UUID authorId,
		String authorName,
		String content,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static TaskCommentResponse from(TaskComment comment) {
		return new TaskCommentResponse(
				comment.getId(),
				comment.getTask().getId(),
				comment.getAuthor().getId(),
				comment.getAuthor().getFullName(),
				comment.getContent(),
				comment.getCreatedAt(),
				comment.getUpdatedAt());
	}
}

package com.example.demo.dto.response;

import com.example.demo.entity.Project;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.ProjectStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectResponse(
		UUID id,
		UUID workspaceId,
		String name,
		String description,
		String key,
		ProjectStatus status,
		LocalDate startDate,
		LocalDate dueDate,
		UUID createdBy,
		String projectManager,
		long memberCount,
		ProjectRole currentUserRole,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime archivedAt) {

	public static ProjectResponse from(
			Project project, String managerName, long memberCount, ProjectRole currentUserRole) {
		return new ProjectResponse(
				project.getId(),
				project.getWorkspace().getId(),
				project.getName(),
				project.getDescription(),
				project.getKey(),
				project.getStatus(),
				project.getStartDate(),
				project.getDueDate(),
				project.getCreatedBy().getId(),
				managerName,
				memberCount,
				currentUserRole,
				project.getCreatedAt(),
				project.getUpdatedAt(),
				project.getArchivedAt());
	}
}

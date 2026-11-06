package com.example.demo.dto.response;

import com.example.demo.entity.Workspace;
import com.example.demo.enums.WorkspaceRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record WorkspaceResponse(
		UUID id,
		String name,
		String description,
		String slug,
		UUID ownerId,
		int memberCount,
		WorkspaceRole currentUserRole,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static WorkspaceResponse from(
			Workspace workspace, int memberCount, WorkspaceRole currentUserRole) {
		return new WorkspaceResponse(
				workspace.getId(),
				workspace.getName(),
				workspace.getDescription(),
				workspace.getSlug(),
				workspace.getOwner().getId(),
				memberCount,
				currentUserRole,
				workspace.getCreatedAt(),
				workspace.getUpdatedAt());
	}
}

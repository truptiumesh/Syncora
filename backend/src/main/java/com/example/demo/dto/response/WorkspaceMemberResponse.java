package com.example.demo.dto.response;

import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.WorkspaceRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record WorkspaceMemberResponse(
		UUID id,
		UUID userId,
		String fullName,
		String email,
		String profileImageUrl,
		WorkspaceRole role,
		LocalDateTime joinedAt) {

	public static WorkspaceMemberResponse from(WorkspaceMember member) {
		return new WorkspaceMemberResponse(
				member.getId(),
				member.getUser().getId(),
				member.getUser().getFullName(),
				member.getUser().getEmail(),
				member.getUser().getProfileImageUrl(),
				member.getRole(),
				member.getJoinedAt());
	}
}

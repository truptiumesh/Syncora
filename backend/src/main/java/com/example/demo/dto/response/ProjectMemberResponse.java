package com.example.demo.dto.response;

import com.example.demo.entity.ProjectMember;
import com.example.demo.enums.ProjectRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectMemberResponse(
		UUID id,
		UUID userId,
		String fullName,
		String email,
		String profileImageUrl,
		ProjectRole role,
		LocalDateTime joinedAt) {

	public static ProjectMemberResponse from(ProjectMember member) {
		return new ProjectMemberResponse(
				member.getId(),
				member.getUser().getId(),
				member.getUser().getFullName(),
				member.getUser().getEmail(),
				member.getUser().getProfileImageUrl(),
				member.getRole(),
				member.getJoinedAt());
	}
}

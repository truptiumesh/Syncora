package com.example.demo.dto.response;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String fullName,
		String email,
		String profileImageUrl,
		String bio,
		Role role,
		boolean active,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getProfileImageUrl(),
				user.getBio(),
				user.getRole(),
				user.isActive(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}

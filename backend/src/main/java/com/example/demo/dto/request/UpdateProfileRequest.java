package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank @Size(min = 2, max = 100) String fullName,
		@Size(max = 500) String bio,
		@Size(max = 2048) String profileImageUrl) {
}

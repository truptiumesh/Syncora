package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 1000) String description) {
}

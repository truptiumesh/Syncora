package com.example.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddWorkspaceMemberRequest(
		@NotBlank @Email @Size(max = 254) String email) {
}

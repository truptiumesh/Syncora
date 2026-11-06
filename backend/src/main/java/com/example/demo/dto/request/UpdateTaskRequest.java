package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTaskRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 4000) String description,
		LocalDate dueDate) {
}

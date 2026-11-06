package com.example.demo.dto.request;

import com.example.demo.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateProjectRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 2000) String description,
		@NotNull ProjectStatus status,
		LocalDate startDate,
		LocalDate dueDate) {
}

package com.example.demo.dto.request;

import com.example.demo.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 4000) String description,
		@NotNull TaskPriority priority,
		UUID assigneeId,
		LocalDate dueDate) {
}

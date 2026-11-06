package com.example.demo.dto.request;

import com.example.demo.enums.TaskPriority;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskPriorityRequest(@NotNull TaskPriority priority) {
}

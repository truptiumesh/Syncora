package com.example.demo.controller;

import com.example.demo.dto.request.CreateTaskRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.enums.TaskPriority;
import com.example.demo.enums.TaskStatus;
import com.example.demo.service.TaskService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
public class ProjectTaskController {
	private final TaskService taskService;

	public ProjectTaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@PostMapping
	public ResponseEntity<TaskResponse> create(
			Authentication authentication,
			@PathVariable UUID projectId,
			@Valid @RequestBody CreateTaskRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(taskService.create(projectId, authentication.getName(), request));
	}

	@GetMapping
	public Page<TaskResponse> list(
			Authentication authentication,
			@PathVariable UUID projectId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) TaskPriority priority,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(defaultValue = "false") boolean overdue,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(defaultValue = "createdAt") String sortBy,
			@RequestParam(defaultValue = "DESC") String sortDirection) {
		return taskService.list(
				projectId, authentication.getName(), search, status, priority, assigneeId,
				overdue, page, size, sortBy, sortDirection);
	}
}

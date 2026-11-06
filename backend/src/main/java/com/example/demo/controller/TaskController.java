package com.example.demo.controller;

import com.example.demo.dto.request.AssignTaskRequest;
import com.example.demo.dto.request.UpdateTaskPriorityRequest;
import com.example.demo.dto.request.UpdateTaskRequest;
import com.example.demo.dto.request.UpdateTaskStatusRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.service.TaskService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}")
public class TaskController {
	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping
	public TaskResponse get(Authentication authentication, @PathVariable UUID taskId) {
		return taskService.get(taskId, authentication.getName());
	}

	@PutMapping
	public TaskResponse update(
			Authentication authentication,
			@PathVariable UUID taskId,
			@Valid @RequestBody UpdateTaskRequest request) {
		return taskService.update(taskId, authentication.getName(), request);
	}

	@PatchMapping("/status")
	public TaskResponse updateStatus(
			Authentication authentication,
			@PathVariable UUID taskId,
			@Valid @RequestBody UpdateTaskStatusRequest request) {
		return taskService.updateStatus(taskId, authentication.getName(), request);
	}

	@PatchMapping("/priority")
	public TaskResponse updatePriority(
			Authentication authentication,
			@PathVariable UUID taskId,
			@Valid @RequestBody UpdateTaskPriorityRequest request) {
		return taskService.updatePriority(taskId, authentication.getName(), request);
	}

	@PatchMapping("/assignee")
	public TaskResponse assign(
			Authentication authentication,
			@PathVariable UUID taskId,
			@Valid @RequestBody AssignTaskRequest request) {
		return taskService.assign(taskId, authentication.getName(), request);
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID taskId) {
		taskService.delete(taskId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

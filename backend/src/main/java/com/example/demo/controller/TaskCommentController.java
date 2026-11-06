package com.example.demo.controller;

import com.example.demo.dto.request.TaskCommentRequest;
import com.example.demo.dto.response.TaskCommentResponse;
import com.example.demo.service.TaskCommentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
public class TaskCommentController {
	private final TaskCommentService taskCommentService;

	public TaskCommentController(TaskCommentService taskCommentService) {
		this.taskCommentService = taskCommentService;
	}

	@GetMapping
	public List<TaskCommentResponse> list(
			Authentication authentication, @PathVariable UUID taskId) {
		return taskCommentService.list(taskId, authentication.getName());
	}

	@PostMapping
	public ResponseEntity<TaskCommentResponse> create(
			Authentication authentication,
			@PathVariable UUID taskId,
			@Valid @RequestBody TaskCommentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(taskCommentService.create(taskId, authentication.getName(), request));
	}

	@PutMapping("/{commentId}")
	public TaskCommentResponse update(
			Authentication authentication,
			@PathVariable UUID taskId,
			@PathVariable UUID commentId,
			@Valid @RequestBody TaskCommentRequest request) {
		return taskCommentService.update(taskId, commentId, authentication.getName(), request);
	}

	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> delete(
			Authentication authentication,
			@PathVariable UUID taskId,
			@PathVariable UUID commentId) {
		taskCommentService.delete(taskId, commentId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

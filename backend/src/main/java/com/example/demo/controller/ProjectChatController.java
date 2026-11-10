package com.example.demo.controller;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.ChatMessageResponse;
import com.example.demo.service.ChatMessageService;
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
@RequestMapping("/api/v1/projects/{projectId}/messages")
public class ProjectChatController {
	private final ChatMessageService chatMessageService;

	public ProjectChatController(ChatMessageService chatMessageService) {
		this.chatMessageService = chatMessageService;
	}

	@GetMapping
	public Page<ChatMessageResponse> history(
			Authentication authentication,
			@PathVariable UUID projectId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return chatMessageService.history(projectId, authentication.getName(), page, size);
	}

	@PostMapping
	public ResponseEntity<ChatMessageResponse> send(
			Authentication authentication,
			@PathVariable UUID projectId,
			@Valid @RequestBody ChatMessageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(chatMessageService.create(projectId, authentication.getName(), request));
	}
}

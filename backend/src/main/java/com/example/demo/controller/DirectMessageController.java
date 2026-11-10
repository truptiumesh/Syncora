package com.example.demo.controller;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.DirectMessageResponse;
import com.example.demo.service.DirectMessageService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/direct-conversations/{conversationId}/messages")
public class DirectMessageController {
	private final DirectMessageService directMessageService;

	public DirectMessageController(DirectMessageService directMessageService) {
		this.directMessageService = directMessageService;
	}

	@GetMapping
	public Page<DirectMessageResponse> history(
			Authentication authentication,
			@PathVariable UUID conversationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return directMessageService.history(conversationId, authentication.getName(), page, size);
	}

	@PostMapping
	public ResponseEntity<DirectMessageResponse> send(
			Authentication authentication,
			@PathVariable UUID conversationId,
			@Valid @RequestBody ChatMessageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(directMessageService.send(conversationId, authentication.getName(), request));
	}

	@MessageMapping("/direct/{conversationId}/messages")
	public void sendOverWebSocket(
			@DestinationVariable UUID conversationId,
			@Valid @Payload ChatMessageRequest request,
			Principal principal) {
		if (principal == null) {
			throw new AccessDeniedException("Authentication is required.");
		}
		directMessageService.send(conversationId, principal.getName(), request);
	}
}

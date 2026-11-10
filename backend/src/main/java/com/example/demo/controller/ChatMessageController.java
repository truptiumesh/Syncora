package com.example.demo.controller;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.ChatMessageResponse;
import com.example.demo.service.ChatMessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/messages")
public class ChatMessageController {
	private final ChatMessageService chatMessageService;

	public ChatMessageController(ChatMessageService chatMessageService) {
		this.chatMessageService = chatMessageService;
	}

	@PutMapping("/{messageId}")
	public ChatMessageResponse update(
			Authentication authentication,
			@PathVariable UUID messageId,
			@Valid @RequestBody ChatMessageRequest request) {
		return chatMessageService.update(messageId, authentication.getName(), request);
	}

	@DeleteMapping("/{messageId}")
	public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID messageId) {
		chatMessageService.delete(messageId, authentication.getName());
		return ResponseEntity.noContent().build();
	}

	@MessageMapping("/projects/{projectId}/messages")
	public void sendOverWebSocket(
			@DestinationVariable UUID projectId,
			@Valid @Payload ChatMessageRequest request,
			Principal principal) {
		if (principal == null) {
			throw new org.springframework.security.access.AccessDeniedException(
					"Authentication is required.");
		}
		chatMessageService.create(projectId, principal.getName(), request);
	}
}

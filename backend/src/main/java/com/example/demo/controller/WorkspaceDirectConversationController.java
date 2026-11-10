package com.example.demo.controller;

import com.example.demo.dto.request.CreateDirectConversationRequest;
import com.example.demo.dto.response.DirectConversationResponse;
import com.example.demo.service.DirectMessageService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/direct-conversations")
public class WorkspaceDirectConversationController {
	private final DirectMessageService directMessageService;

	public WorkspaceDirectConversationController(DirectMessageService directMessageService) {
		this.directMessageService = directMessageService;
	}

	@GetMapping
	public List<DirectConversationResponse> list(
			Authentication authentication, @PathVariable UUID workspaceId) {
		return directMessageService.list(workspaceId, authentication.getName());
	}

	@PostMapping
	public ResponseEntity<DirectConversationResponse> createOrGet(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@Valid @RequestBody CreateDirectConversationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(directMessageService.createOrGetConversation(
				workspaceId, request.recipientId(), authentication.getName()));
	}
}

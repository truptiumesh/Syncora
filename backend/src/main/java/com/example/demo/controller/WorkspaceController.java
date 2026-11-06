package com.example.demo.controller;

import com.example.demo.dto.request.CreateWorkspaceRequest;
import com.example.demo.dto.request.UpdateWorkspaceRequest;
import com.example.demo.dto.response.WorkspaceResponse;
import com.example.demo.service.WorkspaceService;
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
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
	private final WorkspaceService workspaceService;

	public WorkspaceController(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@PostMapping
	public ResponseEntity<WorkspaceResponse> create(
			Authentication authentication, @Valid @RequestBody CreateWorkspaceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(workspaceService.create(authentication.getName(), request));
	}

	@GetMapping
	public List<WorkspaceResponse> listMine(Authentication authentication) {
		return workspaceService.listMine(authentication.getName());
	}

	@GetMapping("/{workspaceId}")
	public WorkspaceResponse get(
			Authentication authentication, @PathVariable UUID workspaceId) {
		return workspaceService.get(workspaceId, authentication.getName());
	}

	@PutMapping("/{workspaceId}")
	public WorkspaceResponse update(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@Valid @RequestBody UpdateWorkspaceRequest request) {
		return workspaceService.update(workspaceId, authentication.getName(), request);
	}

	@DeleteMapping("/{workspaceId}")
	public ResponseEntity<Void> delete(
			Authentication authentication, @PathVariable UUID workspaceId) {
		workspaceService.delete(workspaceId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

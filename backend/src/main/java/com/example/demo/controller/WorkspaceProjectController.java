package com.example.demo.controller;

import com.example.demo.dto.request.CreateProjectRequest;
import com.example.demo.dto.response.ProjectResponse;
import com.example.demo.enums.ProjectStatus;
import com.example.demo.service.ProjectService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects")
public class WorkspaceProjectController {
	private final ProjectService projectService;

	public WorkspaceProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	public ResponseEntity<ProjectResponse> create(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@Valid @RequestBody CreateProjectRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(projectService.create(workspaceId, authentication.getName(), request));
	}

	@GetMapping
	public Page<ProjectResponse> list(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@RequestParam(required = false) ProjectStatus status,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "false") boolean archived,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return projectService.list(
				workspaceId, authentication.getName(), status, search, archived, page, size);
	}
}

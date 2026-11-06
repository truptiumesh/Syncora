package com.example.demo.controller;

import com.example.demo.dto.request.UpdateProjectRequest;
import com.example.demo.dto.response.ProjectResponse;
import com.example.demo.service.ProjectService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectController {
	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public ProjectResponse get(Authentication authentication, @PathVariable UUID projectId) {
		return projectService.get(projectId, authentication.getName());
	}

	@PutMapping
	public ProjectResponse update(
			Authentication authentication,
			@PathVariable UUID projectId,
			@Valid @RequestBody UpdateProjectRequest request) {
		return projectService.update(projectId, authentication.getName(), request);
	}

	@PutMapping("/archive")
	public ProjectResponse archive(Authentication authentication, @PathVariable UUID projectId) {
		return projectService.archive(projectId, authentication.getName());
	}

	@PutMapping("/restore")
	public ProjectResponse restore(Authentication authentication, @PathVariable UUID projectId) {
		return projectService.restore(projectId, authentication.getName());
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID projectId) {
		projectService.delete(projectId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

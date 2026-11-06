package com.example.demo.controller;

import com.example.demo.dto.request.AddProjectMemberRequest;
import com.example.demo.dto.request.UpdateProjectMemberRoleRequest;
import com.example.demo.dto.response.ProjectMemberResponse;
import com.example.demo.service.ProjectMemberService;
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
@RequestMapping("/api/v1/projects/{projectId}/members")
public class ProjectMemberController {
	private final ProjectMemberService projectMemberService;

	public ProjectMemberController(ProjectMemberService projectMemberService) {
		this.projectMemberService = projectMemberService;
	}

	@GetMapping
	public List<ProjectMemberResponse> list(
			Authentication authentication, @PathVariable UUID projectId) {
		return projectMemberService.list(projectId, authentication.getName());
	}

	@PostMapping
	public ResponseEntity<ProjectMemberResponse> add(
			Authentication authentication,
			@PathVariable UUID projectId,
			@Valid @RequestBody AddProjectMemberRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(projectMemberService.add(projectId, authentication.getName(), request));
	}

	@PutMapping("/{memberId}/role")
	public ProjectMemberResponse updateRole(
			Authentication authentication,
			@PathVariable UUID projectId,
			@PathVariable UUID memberId,
			@Valid @RequestBody UpdateProjectMemberRoleRequest request) {
		return projectMemberService.updateRole(
				projectId, memberId, authentication.getName(), request);
	}

	@DeleteMapping("/{memberId}")
	public ResponseEntity<Void> remove(
			Authentication authentication,
			@PathVariable UUID projectId,
			@PathVariable UUID memberId) {
		projectMemberService.remove(projectId, memberId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

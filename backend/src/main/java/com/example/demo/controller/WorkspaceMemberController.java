package com.example.demo.controller;

import com.example.demo.dto.request.AddWorkspaceMemberRequest;
import com.example.demo.dto.request.UpdateWorkspaceMemberRoleRequest;
import com.example.demo.dto.response.WorkspaceMemberResponse;
import com.example.demo.service.WorkspaceMemberService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
public class WorkspaceMemberController {
	private final WorkspaceMemberService memberService;

	public WorkspaceMemberController(WorkspaceMemberService memberService) {
		this.memberService = memberService;
	}

	@GetMapping
	public List<WorkspaceMemberResponse> list(
			Authentication authentication, @PathVariable UUID workspaceId) {
		return memberService.list(workspaceId, authentication.getName());
	}

	@PostMapping
	public ResponseEntity<WorkspaceMemberResponse> add(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@Valid @RequestBody AddWorkspaceMemberRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(memberService.add(workspaceId, authentication.getName(), request));
	}

	@PutMapping("/{memberId}/role")
	public WorkspaceMemberResponse updateRole(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@PathVariable UUID memberId,
			@Valid @RequestBody UpdateWorkspaceMemberRoleRequest request) {
		return memberService.updateRole(workspaceId, memberId, authentication.getName(), request);
	}

	@DeleteMapping("/{memberId}")
	public ResponseEntity<Void> remove(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@PathVariable UUID memberId) {
		memberService.remove(workspaceId, memberId, authentication.getName());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> leave(
			Authentication authentication, @PathVariable UUID workspaceId) {
		memberService.leave(workspaceId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

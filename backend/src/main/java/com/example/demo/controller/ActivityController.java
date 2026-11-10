package com.example.demo.controller;

import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.service.ActivityService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ActivityController {
	private final ActivityService activityService;

	public ActivityController(ActivityService activityService) {
		this.activityService = activityService;
	}

	@GetMapping("/activity")
	public Page<ActivityResponse> listMine(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return activityService.listMine(authentication.getName(), page, size);
	}

	@GetMapping("/workspaces/{workspaceId}/activity")
	public Page<ActivityResponse> listWorkspace(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return activityService.listWorkspace(
				workspaceId, authentication.getName(), page, size);
	}

	@GetMapping("/projects/{projectId}/activity")
	public Page<ActivityResponse> listProject(
			Authentication authentication,
			@PathVariable UUID projectId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return activityService.listProject(projectId, authentication.getName(), page, size);
	}
}

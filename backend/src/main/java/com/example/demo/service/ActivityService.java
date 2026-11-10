package com.example.demo.service;

import com.example.demo.dto.response.ActivityResponse;
import com.example.demo.entity.User;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.repository.ActivityRecordRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {
	private static final Set<WorkspaceRole> WORKSPACE_ADMIN_ROLES =
			Set.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

	private final ActivityRecordRepository activityRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ProjectAuthorizationService projectAuthorizationService;

	public ActivityService(
			ActivityRecordRepository activityRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ProjectAuthorizationService projectAuthorizationService) {
		this.activityRepository = activityRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.projectAuthorizationService = projectAuthorizationService;
	}

	@Transactional(readOnly = true)
	public Page<ActivityResponse> listMine(String principalName, int page, int size) {
		User user = workspaceAuthorizationService.findActiveUser(principalName);
		return activityRepository.findVisibleToUser(
						user.getId(), WORKSPACE_ADMIN_ROLES, pageable(page, size))
				.map(ActivityResponse::from);
	}

	@Transactional(readOnly = true)
	public Page<ActivityResponse> listWorkspace(
			UUID workspaceId, String principalName, int page, int size) {
		workspaceAuthorizationService.requireMember(workspaceId, principalName);
		User user = workspaceAuthorizationService.findActiveUser(principalName);
		return activityRepository.findVisibleToUserInWorkspace(
						user.getId(), workspaceId, WORKSPACE_ADMIN_ROLES, pageable(page, size))
				.map(ActivityResponse::from);
	}

	@Transactional(readOnly = true)
	public Page<ActivityResponse> listProject(UUID projectId, String principalName, int page, int size) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
		return activityRepository.findByProject_IdOrderByCreatedAtDescIdDesc(
						projectId, pageable(page, size))
				.map(ActivityResponse::from);
	}

	private PageRequest pageable(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
		return PageRequest.of(
				page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}
}

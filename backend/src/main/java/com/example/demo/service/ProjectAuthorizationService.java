package com.example.demo.service;

import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectMember;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.exception.ProjectNotFoundException;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProjectAuthorizationService {
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;

	public ProjectAuthorizationService(
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService) {
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
	}

	public Project findProject(UUID projectId) {
		return projectRepository.findById(projectId).orElseThrow(ProjectNotFoundException::new);
	}

	public Access requireProjectAccess(UUID projectId, String principalName) {
		Project project = findProject(projectId);
		WorkspaceMember workspaceMember = workspaceAuthorizationService.requireMember(
				project.getWorkspace().getId(), principalName);
		ProjectMember projectMember = projectMemberRepository
				.findByProject_IdAndUser_Id(projectId, workspaceMember.getUser().getId())
				.orElse(null);
		if (!isWorkspaceAdmin(workspaceMember) && projectMember == null) {
			throw new AccessDeniedException("You do not have access to this project.");
		}
		return new Access(project, workspaceMember, projectMember);
	}

	public Access requireProjectManagerOrWorkspaceAdmin(UUID projectId, String principalName) {
		Access access = requireProjectAccess(projectId, principalName);
		if (isWorkspaceAdmin(access.workspaceMember())) {
			return access;
		}
		if (access.projectMember() == null
				|| access.projectMember().getRole() != ProjectRole.PROJECT_MANAGER) {
			throw new AccessDeniedException("Project manager permissions are required.");
		}
		return access;
	}

	public Access requireWorkspaceAdmin(UUID projectId, String principalName) {
		Access access = requireProjectAccess(projectId, principalName);
		if (!isWorkspaceAdmin(access.workspaceMember())) {
			throw new AccessDeniedException("Workspace owner or admin permissions are required.");
		}
		return access;
	}

	public boolean isWorkspaceAdmin(WorkspaceMember member) {
		return member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN;
	}

	public record Access(
			Project project,
			WorkspaceMember workspaceMember,
			ProjectMember projectMember) {
	}
}

package com.example.demo.service;

import com.example.demo.dto.request.AddProjectMemberRequest;
import com.example.demo.dto.request.UpdateProjectMemberRoleRequest;
import com.example.demo.dto.response.ProjectMemberResponse;
import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectMember;
import com.example.demo.entity.User;
import com.example.demo.enums.ProjectRole;
import com.example.demo.exception.LastProjectManagerException;
import com.example.demo.exception.ProjectMemberExistsException;
import com.example.demo.exception.ProjectMemberNotFoundException;
import com.example.demo.exception.ProjectNotFoundException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {
	private final ProjectMemberRepository projectMemberRepository;
	private final ProjectRepository projectRepository;
	private final WorkspaceMemberRepository workspaceMemberRepository;
	private final UserRepository userRepository;
	private final ProjectAuthorizationService authorizationService;

	public ProjectMemberService(
			ProjectMemberRepository projectMemberRepository,
			ProjectRepository projectRepository,
			WorkspaceMemberRepository workspaceMemberRepository,
			UserRepository userRepository,
			ProjectAuthorizationService authorizationService) {
		this.projectMemberRepository = projectMemberRepository;
		this.projectRepository = projectRepository;
		this.workspaceMemberRepository = workspaceMemberRepository;
		this.userRepository = userRepository;
		this.authorizationService = authorizationService;
	}

	@Transactional(readOnly = true)
	public List<ProjectMemberResponse> list(UUID projectId, String principalName) {
		authorizationService.requireProjectAccess(projectId, principalName);
		return projectMemberRepository.findByProject_IdOrderByJoinedAtAsc(projectId).stream()
				.map(ProjectMemberResponse::from)
				.toList();
	}

	@Transactional
	public ProjectMemberResponse add(
			UUID projectId, String principalName, AddProjectMemberRequest request) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		Project project = authorizationService
				.requireProjectManagerOrWorkspaceAdmin(projectId, principalName).project();
		User user = userRepository.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
				.filter(User::isActive)
				.orElseThrow(UserNotFoundException::new);
		UUID workspaceId = project.getWorkspace().getId();
		if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, user.getId())) {
			throw new AccessDeniedException("Project members must already belong to the workspace.");
		}
		if (projectMemberRepository.existsByProject_IdAndUser_Id(projectId, user.getId())) {
			throw new ProjectMemberExistsException();
		}

		ProjectMember member = new ProjectMember();
		member.setProject(project);
		member.setUser(user);
		member.setRole(ProjectRole.MEMBER);
		return ProjectMemberResponse.from(projectMemberRepository.save(member));
	}

	@Transactional
	public ProjectMemberResponse updateRole(
			UUID projectId,
			UUID memberId,
			String principalName,
			UpdateProjectMemberRoleRequest request) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		authorizationService.requireProjectManagerOrWorkspaceAdmin(projectId, principalName);
		ProjectMember target = findMember(projectId, memberId);
		if (target.getRole() == ProjectRole.PROJECT_MANAGER
				&& request.role() == ProjectRole.MEMBER
				&& projectMemberRepository.countByProject_IdAndRole(projectId, ProjectRole.PROJECT_MANAGER) == 1) {
			throw new LastProjectManagerException();
		}
		target.setRole(request.role());
		return ProjectMemberResponse.from(projectMemberRepository.save(target));
	}

	@Transactional
	public void remove(UUID projectId, UUID memberId, String principalName) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		authorizationService.requireProjectManagerOrWorkspaceAdmin(projectId, principalName);
		ProjectMember target = findMember(projectId, memberId);
		if (target.getRole() == ProjectRole.PROJECT_MANAGER
				&& projectMemberRepository.countByProject_IdAndRole(projectId, ProjectRole.PROJECT_MANAGER) == 1) {
			throw new LastProjectManagerException();
		}
		projectMemberRepository.delete(target);
	}

	private ProjectMember findMember(UUID projectId, UUID memberId) {
		return projectMemberRepository.findByIdAndProject_Id(memberId, projectId)
				.orElseThrow(ProjectMemberNotFoundException::new);
	}
}

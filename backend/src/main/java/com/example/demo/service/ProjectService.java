package com.example.demo.service;

import com.example.demo.dto.request.CreateProjectRequest;
import com.example.demo.dto.request.UpdateProjectRequest;
import com.example.demo.dto.response.ProjectResponse;
import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectMember;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.ProjectStatus;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
import com.example.demo.exception.ProjectNotFoundException;
import com.example.demo.exception.WorkspaceNotFoundException;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.service.ProjectAuthorizationService.Access;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ProjectAuthorizationService projectAuthorizationService;
	private final ApplicationEventPublisher eventPublisher;
	private final FileAttachmentService fileAttachmentService;

	public ProjectService(
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ProjectAuthorizationService projectAuthorizationService,
			ApplicationEventPublisher eventPublisher,
			FileAttachmentService fileAttachmentService) {
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.workspaceRepository = workspaceRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.projectAuthorizationService = projectAuthorizationService;
		this.eventPublisher = eventPublisher;
		this.fileAttachmentService = fileAttachmentService;
	}

	@Transactional
	public ProjectResponse create(
			UUID workspaceId, String principalName, CreateProjectRequest request) {
		WorkspaceMember creator = workspaceAuthorizationService.requireMember(workspaceId, principalName);
		Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
				.orElseThrow(WorkspaceNotFoundException::new);
		validateDates(request.startDate(), request.dueDate());
		if (request.status() == ProjectStatus.ARCHIVED) {
			throw new IllegalArgumentException("A new project cannot start archived.");
		}

		Project project = new Project();
		project.setWorkspace(workspace);
		project.setName(request.name().trim());
		project.setDescription(normalizeDescription(request.description()));
		project.setKey(createUniqueKey(workspaceId, request.name()));
		project.setStatus(request.status());
		project.setStartDate(request.startDate());
		project.setDueDate(request.dueDate());
		project.setCreatedBy(creator.getUser());
		project = projectRepository.saveAndFlush(project);

		ProjectMember manager = new ProjectMember();
		manager.setProject(project);
		manager.setUser(creator.getUser());
		manager.setRole(ProjectRole.PROJECT_MANAGER);
		projectMemberRepository.save(manager);
		eventPublisher.publishEvent(ActivityEvent.create(
				creator.getUser(),
				ActivityType.PROJECT_CREATED,
				creator.getUser().getFullName() + " created project " + project.getName() + ".",
				workspaceId,
				project.getId(),
				null,
				null,
				null,
				List.of()));
		return response(project, creator.getUser().getFullName(), 1, ProjectRole.PROJECT_MANAGER);
	}

	@Transactional(readOnly = true)
	public Page<ProjectResponse> list(
			UUID workspaceId,
			String principalName,
			ProjectStatus status,
			String search,
			boolean archived,
			int page,
			int size) {
		WorkspaceMember actor = workspaceAuthorizationService.requireMember(workspaceId, principalName);
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
		boolean workspaceAdmin = projectAuthorizationService.isWorkspaceAdmin(actor);
		String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Project> projects = projectRepository.searchVisible(
				workspaceId, actor.getUser().getId(), workspaceAdmin, status, normalizedSearch, archived, pageable);
		if (projects.isEmpty()) {
			return projects.map(project -> response(project, null, 0, null));
		}

		Collection<UUID> projectIds = projects.getContent().stream().map(Project::getId).toList();
		Map<UUID, String> managers = projectMemberRepository
				.findManagerNames(projectIds, ProjectRole.PROJECT_MANAGER).stream()
				.collect(Collectors.toMap(
						ProjectMemberRepository.ManagerName::getProjectId,
						ProjectMemberRepository.ManagerName::getFullName,
						(first, ignored) -> first));
		Map<UUID, Long> counts = projectMemberRepository.countMembersByProjectIds(projectIds).stream()
				.collect(Collectors.toMap(
						ProjectMemberRepository.MemberCount::getProjectId,
						ProjectMemberRepository.MemberCount::getMemberCount));
		Map<UUID, ProjectRole> roles = projectMemberRepository
				.findRolesForUser(projectIds, actor.getUser().getId()).stream()
				.collect(Collectors.toMap(
						ProjectMemberRepository.UserProjectRole::getProjectId,
						ProjectMemberRepository.UserProjectRole::getRole));
		return projects.map(project -> response(
				project,
				managers.get(project.getId()),
				counts.getOrDefault(project.getId(), 0L),
				roles.get(project.getId())));
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(UUID projectId, String principalName) {
		Access access = projectAuthorizationService.requireProjectAccess(projectId, principalName);
		return responseFor(access.project(), access.projectMember());
	}

	@Transactional
	public ProjectResponse update(
			UUID projectId, String principalName, UpdateProjectRequest request) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		Access access = projectAuthorizationService.requireProjectManagerOrWorkspaceAdmin(projectId, principalName);
		Project project = access.project();
		if (project.getArchivedAt() != null) {
			throw new IllegalArgumentException("Restore the project before updating it.");
		}
		if (request.status() == ProjectStatus.ARCHIVED) {
			throw new IllegalArgumentException("Use the archive endpoint to archive a project.");
		}
		validateDates(request.startDate(), request.dueDate());
		project.setName(request.name().trim());
		project.setDescription(normalizeDescription(request.description()));
		project.setStatus(request.status());
		project.setStartDate(request.startDate());
		project.setDueDate(request.dueDate());
		publishProjectUpdate(
				workspaceAuthorizationService.findActiveUser(principalName),
				project,
				ActivityType.PROJECT_UPDATED,
				"updated project " + project.getName() + ".");
		return responseFor(project, access.projectMember());
	}

	@Transactional
	public ProjectResponse archive(UUID projectId, String principalName) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		Access access = projectAuthorizationService.requireProjectManagerOrWorkspaceAdmin(projectId, principalName);
		Project project = access.project();
		if (project.getArchivedAt() == null) {
			project.setStatus(ProjectStatus.ARCHIVED);
			project.setArchivedAt(LocalDateTime.now(Clock.systemUTC()));
			publishProjectUpdate(
					workspaceAuthorizationService.findActiveUser(principalName),
					project,
					ActivityType.PROJECT_UPDATED,
					"archived project " + project.getName() + ".");
		}
		return responseFor(project, access.projectMember());
	}

	@Transactional
	public ProjectResponse restore(UUID projectId, String principalName) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		Access access = projectAuthorizationService.requireProjectManagerOrWorkspaceAdmin(projectId, principalName);
		Project project = access.project();
		if (project.getArchivedAt() != null) {
			project.setStatus(ProjectStatus.ACTIVE);
			project.setArchivedAt(null);
			publishProjectUpdate(
					workspaceAuthorizationService.findActiveUser(principalName),
					project,
					ActivityType.PROJECT_UPDATED,
					"restored project " + project.getName() + ".");
		}
		return responseFor(project, access.projectMember());
	}

	@Transactional
	public void delete(UUID projectId, String principalName) {
		projectRepository.findByIdForUpdate(projectId).orElseThrow(ProjectNotFoundException::new);
		Access access = projectAuthorizationService.requireWorkspaceAdmin(projectId, principalName);
		fileAttachmentService.scheduleCleanupForProject(projectId);
		projectMemberRepository.deleteAll(
				projectMemberRepository.findByProject_IdOrderByJoinedAtAsc(projectId));
		projectRepository.delete(access.project());
	}

	private ProjectResponse responseFor(Project project, ProjectMember currentMember) {
		ProjectMember manager = projectMemberRepository
				.findByProject_IdAndRole(project.getId(), ProjectRole.PROJECT_MANAGER)
				.orElseThrow(ProjectNotFoundException::new);
		return response(
				project,
				manager.getUser().getFullName(),
				projectMemberRepository.countByProject_Id(project.getId()),
				currentMember == null ? null : currentMember.getRole());
	}

	private void publishProjectUpdate(
			com.example.demo.entity.User actor,
			Project project,
			ActivityType type,
			String action) {
		List<ActivityEvent.NotificationTarget> notifications = projectMemberRepository
				.findByProject_IdOrderByJoinedAtAsc(project.getId()).stream()
				.filter(member -> member.getUser().isActive())
				.map(member -> new ActivityEvent.NotificationTarget(
						member.getUser().getId(),
						NotificationType.PROJECT_UPDATED,
						"Project updated",
						"Project " + project.getName() + " was updated."))
				.toList();
		eventPublisher.publishEvent(ActivityEvent.create(
				actor,
				type,
				actor.getFullName() + " " + action,
				project.getWorkspace().getId(),
				project.getId(),
				null,
				null,
				null,
				notifications));
	}

	private ProjectResponse response(
			Project project, String managerName, long count, ProjectRole currentRole) {
		return ProjectResponse.from(project, managerName, count, currentRole);
	}

	private String createUniqueKey(UUID workspaceId, String name) {
		String base = Normalizer.normalize(name, Normalizer.Form.NFKD)
				.replaceAll("\\p{M}", "")
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]+", "-")
				.replaceAll("(^-+|-+$)", "");
		if (base.isEmpty()) {
			base = "PROJECT";
		}
		base = base.substring(0, Math.min(base.length(), 50));
		String candidate = base;
		int suffix = 2;
		while (projectRepository.existsByWorkspace_IdAndKey(workspaceId, candidate)) {
			String tail = "-" + suffix++;
			candidate = base.substring(0, Math.min(base.length(), 50 - tail.length())) + tail;
		}
		return candidate;
	}

	private void validateDates(LocalDate startDate, LocalDate dueDate) {
		if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Due date must be on or after the start date.");
		}
	}

	private String normalizeDescription(String description) {
		return description == null || description.isBlank() ? null : description.trim();
	}
}

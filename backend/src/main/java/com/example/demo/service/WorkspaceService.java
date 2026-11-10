package com.example.demo.service;

import com.example.demo.dto.request.CreateWorkspaceRequest;
import com.example.demo.dto.request.UpdateWorkspaceRequest;
import com.example.demo.dto.response.WorkspaceResponse;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final WorkspaceAuthorizationService authorizationService;
	private final ApplicationEventPublisher eventPublisher;
	private final FileAttachmentService fileAttachmentService;

	public WorkspaceService(
			WorkspaceRepository workspaceRepository,
			WorkspaceMemberRepository memberRepository,
			WorkspaceAuthorizationService authorizationService,
			ApplicationEventPublisher eventPublisher,
			FileAttachmentService fileAttachmentService) {
		this.workspaceRepository = workspaceRepository;
		this.memberRepository = memberRepository;
		this.authorizationService = authorizationService;
		this.eventPublisher = eventPublisher;
		this.fileAttachmentService = fileAttachmentService;
	}

	@Transactional
	public WorkspaceResponse create(String principalName, CreateWorkspaceRequest request) {
		User owner = authorizationService.findActiveUser(principalName);
		Workspace workspace = new Workspace();
		workspace.setName(request.name().trim());
		workspace.setDescription(normalizeDescription(request.description()));
		workspace.setSlug(createSlug(request.name()));
		workspace.setOwner(owner);
		workspace = workspaceRepository.saveAndFlush(workspace);

		WorkspaceMember membership = new WorkspaceMember();
		membership.setWorkspace(workspace);
		membership.setUser(owner);
		membership.setRole(WorkspaceRole.OWNER);
		memberRepository.save(membership);

		eventPublisher.publishEvent(ActivityEvent.create(
				owner,
				ActivityType.WORKSPACE_CREATED,
				owner.getFullName() + " created workspace " + workspace.getName() + ".",
				workspace.getId(),
				null,
				null,
				null,
				null,
				List.of()));
		return WorkspaceResponse.from(workspace, 1, WorkspaceRole.OWNER);
	}

	@Transactional(readOnly = true)
	public List<WorkspaceResponse> listMine(String principalName) {
		User user = authorizationService.findActiveUser(principalName);
		return memberRepository.findByUser_IdOrderByWorkspace_CreatedAtDesc(user.getId()).stream()
				.map(member -> WorkspaceResponse.from(
						member.getWorkspace(),
						Math.toIntExact(memberRepository.countByWorkspace_Id(member.getWorkspace().getId())),
						member.getRole()))
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkspaceResponse get(UUID workspaceId, String principalName) {
		WorkspaceMember member = authorizationService.requireMember(workspaceId, principalName);
		Workspace workspace = member.getWorkspace();
		return WorkspaceResponse.from(
				workspace,
				Math.toIntExact(memberRepository.countByWorkspace_Id(workspaceId)),
				member.getRole());
	}

	@Transactional
	public WorkspaceResponse update(
			UUID workspaceId, String principalName, UpdateWorkspaceRequest request) {
		WorkspaceMember member = authorizationService.requireMember(workspaceId, principalName);
		authorizationService.requireRole(member, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
		Workspace workspace = member.getWorkspace();
		workspace.setName(request.name().trim());
		workspace.setDescription(normalizeDescription(request.description()));
		workspace = workspaceRepository.save(workspace);
		String workspaceName = workspace.getName();
		List<ActivityEvent.NotificationTarget> notifications = memberRepository
				.findByWorkspace_IdOrderByJoinedAtAsc(workspaceId).stream()
				.filter(workspaceMember -> workspaceMember.getUser().isActive())
				.map(workspaceMember -> new ActivityEvent.NotificationTarget(
						workspaceMember.getUser().getId(),
						NotificationType.WORKSPACE_UPDATED,
						"Workspace updated",
						"Workspace " + workspaceName + " was updated."))
				.toList();
		eventPublisher.publishEvent(ActivityEvent.create(
				member.getUser(),
				ActivityType.WORKSPACE_UPDATED,
				member.getUser().getFullName() + " updated workspace " + workspace.getName() + ".",
				workspaceId,
				null,
				null,
				null,
				null,
				notifications));
		return WorkspaceResponse.from(
				workspace,
				Math.toIntExact(memberRepository.countByWorkspace_Id(workspaceId)),
				member.getRole());
	}

	@Transactional
	public void delete(UUID workspaceId, String principalName) {
		WorkspaceMember member = authorizationService.requireMember(workspaceId, principalName);
		authorizationService.requireRole(member, WorkspaceRole.OWNER);
		fileAttachmentService.scheduleCleanupForWorkspace(workspaceId);
		memberRepository.deleteByWorkspace_Id(workspaceId);
		workspaceRepository.delete(member.getWorkspace());
	}

	private String normalizeDescription(String description) {
		return description == null || description.isBlank() ? null : description.trim();
	}

	private String createSlug(String name) {
		String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD)
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\p{M}", "")
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-+|-+$)", "");
		if (normalized.isEmpty()) {
			normalized = "workspace";
		}
		if (normalized.length() > 100) {
			normalized = normalized.substring(0, 100).replaceAll("-+$", "");
		}
		return normalized + "-" + UUID.randomUUID();
	}
}

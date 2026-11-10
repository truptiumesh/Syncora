package com.example.demo.service;

import com.example.demo.dto.request.AddWorkspaceMemberRequest;
import com.example.demo.dto.request.UpdateWorkspaceMemberRoleRequest;
import com.example.demo.dto.response.WorkspaceMemberResponse;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
import com.example.demo.exception.WorkspaceMemberExistsException;
import com.example.demo.exception.WorkspaceMemberNotFoundException;
import com.example.demo.exception.WorkspaceOwnerCannotLeaveException;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceMemberService {
	private final UserRepository userRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final WorkspaceAuthorizationService authorizationService;
	private final ApplicationEventPublisher eventPublisher;

	public WorkspaceMemberService(
			UserRepository userRepository,
			WorkspaceMemberRepository memberRepository,
			WorkspaceAuthorizationService authorizationService,
			ApplicationEventPublisher eventPublisher) {
		this.userRepository = userRepository;
		this.memberRepository = memberRepository;
		this.authorizationService = authorizationService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public List<WorkspaceMemberResponse> list(UUID workspaceId, String principalName) {
		authorizationService.requireMember(workspaceId, principalName);
		return memberRepository.findByWorkspace_IdOrderByJoinedAtAsc(workspaceId).stream()
				.map(WorkspaceMemberResponse::from)
				.toList();
	}

	@Transactional
	public WorkspaceMemberResponse add(
			UUID workspaceId, String principalName, AddWorkspaceMemberRequest request) {
		WorkspaceMember actor = authorizationService.requireMember(workspaceId, principalName);
		authorizationService.requireRole(actor, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(email)
				.filter(User::isActive)
				.orElseThrow(com.example.demo.exception.UserNotFoundException::new);
		if (memberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, user.getId())) {
			throw new WorkspaceMemberExistsException();
		}

		WorkspaceMember member = new WorkspaceMember();
		member.setWorkspace(actor.getWorkspace());
		member.setUser(user);
		member.setRole(WorkspaceRole.MEMBER);
		member = memberRepository.saveAndFlush(member);
		eventPublisher.publishEvent(ActivityEvent.create(
				actor.getUser(),
				ActivityType.WORKSPACE_MEMBER_ADDED,
				actor.getUser().getFullName() + " added " + user.getFullName()
						+ " to workspace " + actor.getWorkspace().getName() + ".",
				workspaceId,
				null,
				null,
				null,
				null,
				List.of(new ActivityEvent.NotificationTarget(
						user.getId(),
						NotificationType.WORKSPACE_MEMBER_ADDED,
						"Added to a workspace",
						"You were added to workspace " + actor.getWorkspace().getName() + "."))));
		return WorkspaceMemberResponse.from(member);
	}

	@Transactional
	public WorkspaceMemberResponse updateRole(
			UUID workspaceId,
			UUID memberId,
			String principalName,
			UpdateWorkspaceMemberRoleRequest request) {
		WorkspaceMember actor = authorizationService.requireMember(workspaceId, principalName);
		WorkspaceMember target = findMember(workspaceId, memberId);
		if (target.getRole() == WorkspaceRole.OWNER || request.role() == WorkspaceRole.OWNER) {
			throw new AccessDeniedException("Workspace ownership cannot be changed through member role operations.");
		}

		if (actor.getRole() == WorkspaceRole.OWNER) {
			target.setRole(request.role());
		} else if (actor.getRole() == WorkspaceRole.ADMIN) {
			if (target.getRole() != WorkspaceRole.MEMBER) {
				throw new AccessDeniedException("Only the workspace owner can change an admin's role.");
			}
			if (request.role() != WorkspaceRole.ADMIN && request.role() != WorkspaceRole.MEMBER) {
				throw new AccessDeniedException("Admins cannot assign this role.");
			}
			target.setRole(request.role());
		} else {
			throw new AccessDeniedException("Your workspace role does not allow this action.");
		}
		return WorkspaceMemberResponse.from(memberRepository.save(target));
	}

	@Transactional
	public void remove(UUID workspaceId, UUID memberId, String principalName) {
		WorkspaceMember actor = authorizationService.requireMember(workspaceId, principalName);
		WorkspaceMember target = findMember(workspaceId, memberId);
		if (target.getRole() == WorkspaceRole.OWNER) {
			throw new AccessDeniedException("The workspace owner cannot be removed.");
		}
		if (target.getRole() == WorkspaceRole.ADMIN) {
			authorizationService.requireRole(actor, WorkspaceRole.OWNER);
		} else {
			authorizationService.requireRole(actor, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
		}
		memberRepository.delete(target);
	}

	@Transactional
	public void leave(UUID workspaceId, String principalName) {
		WorkspaceMember member = authorizationService.requireMember(workspaceId, principalName);
		if (member.getRole() == WorkspaceRole.OWNER) {
			throw new WorkspaceOwnerCannotLeaveException();
		}
		memberRepository.delete(member);
	}

	private WorkspaceMember findMember(UUID workspaceId, UUID memberId) {
		return memberRepository.findByIdAndWorkspace_Id(memberId, workspaceId)
				.orElseThrow(WorkspaceMemberNotFoundException::new);
	}
}

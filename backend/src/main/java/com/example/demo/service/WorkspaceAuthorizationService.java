package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.exception.WorkspaceNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAuthorizationService {
	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository memberRepository;

	public WorkspaceAuthorizationService(
			UserRepository userRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceMemberRepository memberRepository) {
		this.userRepository = userRepository;
		this.workspaceRepository = workspaceRepository;
		this.memberRepository = memberRepository;
	}

	public User findActiveUser(String principalName) {
		return userRepository.findByEmail(principalName.trim().toLowerCase(Locale.ROOT))
				.filter(User::isActive)
				.orElseThrow(UserNotFoundException::new);
	}

	public Workspace findWorkspace(UUID workspaceId) {
		return workspaceRepository.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
	}

	public WorkspaceMember requireMember(UUID workspaceId, String principalName) {
		User user = findActiveUser(principalName);
		return memberRepository.findByWorkspace_IdAndUser_Id(workspaceId, user.getId())
				.orElseThrow(() -> {
					if (!workspaceRepository.existsById(workspaceId)) {
						return new WorkspaceNotFoundException();
					}
					return new AccessDeniedException("You are not a member of this workspace.");
				});
	}

	public void requireRole(WorkspaceMember member, WorkspaceRole... allowedRoles) {
		Set<WorkspaceRole> allowed = Set.of(allowedRoles);
		if (!allowed.contains(member.getRole())) {
			throw new AccessDeniedException("Your workspace role does not allow this action.");
		}
	}
}

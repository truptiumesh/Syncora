package com.example.demo.service;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.ChatMessageResponse;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.exception.ChatMessageNotFoundException;
import com.example.demo.repository.ChatMessageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageService {
	private static final int MAX_PAGE_SIZE = 100;

	private final ChatMessageRepository chatMessageRepository;
	private final ProjectAuthorizationService projectAuthorizationService;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ApplicationEventPublisher eventPublisher;

	public ChatMessageService(
			ChatMessageRepository chatMessageRepository,
			ProjectAuthorizationService projectAuthorizationService,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ApplicationEventPublisher eventPublisher) {
		this.chatMessageRepository = chatMessageRepository;
		this.projectAuthorizationService = projectAuthorizationService;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public Page<ChatMessageResponse> history(UUID projectId, String principalName, int page, int size) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
		validatePage(page, size);
		return chatMessageRepository.findByProject_IdOrderByCreatedAtAscIdAsc(
				projectId, PageRequest.of(page, size)).map(ChatMessageResponse::from);
	}

	@Transactional(readOnly = true)
	public void authorizeProject(UUID projectId, String principalName) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
	}

	@Transactional
	public ChatMessageResponse create(UUID projectId, String principalName, ChatMessageRequest request) {
		var access = projectAuthorizationService.requireProjectAccess(projectId, principalName);
		ensureProjectActive(access.project());
		User sender = workspaceAuthorizationService.findActiveUser(principalName);
		ChatMessage message = new ChatMessage();
		message.setProject(access.project());
		message.setSender(sender);
		message.setContent(request.content().trim());
		ChatMessageResponse response = ChatMessageResponse.from(chatMessageRepository.saveAndFlush(message));
		eventPublisher.publishEvent(new ChatMessageChangedEvent(projectId, response));
		publishActivity(
				sender,
				message,
				ActivityType.CHAT_MESSAGE_SENT,
				"shared a message in project " + access.project().getName() + ".");
		return response;
	}

	@Transactional
	public ChatMessageResponse update(UUID messageId, String principalName, ChatMessageRequest request) {
		ChatMessage message = findForUpdate(messageId);
		var access = projectAuthorizationService.requireProjectAccess(
				message.getProject().getId(), principalName);
		ensureProjectActive(access.project());
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		requireOwnerOrModerator(message, actor, access);
		if (message.getDeletedAt() != null) {
			throw new IllegalArgumentException("A deleted message cannot be edited.");
		}
		message.setContent(request.content().trim());
		message.setEditedAt(LocalDateTime.now(Clock.systemUTC()));
		ChatMessageResponse response = ChatMessageResponse.from(chatMessageRepository.saveAndFlush(message));
		eventPublisher.publishEvent(new ChatMessageChangedEvent(message.getProject().getId(), response));
		publishActivity(
				actor,
				message,
				ActivityType.CHAT_MESSAGE_EDITED,
				"edited a message in project " + access.project().getName() + ".");
		return response;
	}

	@Transactional
	public void delete(UUID messageId, String principalName) {
		ChatMessage message = findForUpdate(messageId);
		var access = projectAuthorizationService.requireProjectAccess(
				message.getProject().getId(), principalName);
		ensureProjectActive(access.project());
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		requireOwnerOrModerator(message, actor, access);
		if (message.getDeletedAt() == null) {
			message.setContent("");
			message.setDeletedAt(LocalDateTime.now(Clock.systemUTC()));
		}
		ChatMessageResponse response = ChatMessageResponse.from(chatMessageRepository.saveAndFlush(message));
		eventPublisher.publishEvent(new ChatMessageChangedEvent(message.getProject().getId(), response));
		publishActivity(
				actor,
				message,
				ActivityType.CHAT_MESSAGE_DELETED,
				"deleted a message in project " + access.project().getName() + ".");
	}

	private ChatMessage findForUpdate(UUID messageId) {
		return chatMessageRepository.findByIdForUpdate(messageId)
				.orElseThrow(ChatMessageNotFoundException::new);
	}

	private void requireOwnerOrModerator(
			ChatMessage message,
			User actor,
			ProjectAuthorizationService.Access access) {
		if (message.getSender().getId().equals(actor.getId())) {
			return;
		}
		boolean workspaceModerator = access.workspaceMember().getRole() == WorkspaceRole.OWNER
				|| access.workspaceMember().getRole() == WorkspaceRole.ADMIN;
		boolean projectManager = access.projectMember() != null
				&& access.projectMember().getRole() == ProjectRole.PROJECT_MANAGER;
		if (!workspaceModerator && !projectManager) {
			throw new AccessDeniedException("Only the sender or a project moderator can change this message.");
		}
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
	}

	private void ensureProjectActive(Project project) {
		if (project.getArchivedAt() != null) {
			throw new IllegalArgumentException("Messages cannot be changed while the project is archived.");
		}
	}

	private void publishActivity(
			User actor, ChatMessage message, ActivityType type, String action) {
		Project project = message.getProject();
		eventPublisher.publishEvent(ActivityEvent.create(
				actor,
				type,
				actor.getFullName() + " " + action,
				project.getWorkspace().getId(),
				project.getId(),
				null,
				null,
				message.getId(),
				java.util.List.of()));
	}
}

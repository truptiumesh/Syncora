package com.example.demo.service;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.DirectConversationResponse;
import com.example.demo.dto.response.DirectMessageResponse;
import com.example.demo.entity.DirectConversation;
import com.example.demo.entity.DirectMessage;
import com.example.demo.entity.User;
import com.example.demo.exception.DirectConversationNotFoundException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.DirectConversationRepository;
import com.example.demo.repository.DirectMessageRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectMessageService {
	private static final int MAX_PAGE_SIZE = 100;

	private final DirectConversationRepository conversationRepository;
	private final DirectMessageRepository messageRepository;
	private final UserRepository userRepository;
	private final WorkspaceMemberRepository workspaceMemberRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ApplicationEventPublisher eventPublisher;

	public DirectMessageService(
			DirectConversationRepository conversationRepository,
			DirectMessageRepository messageRepository,
			UserRepository userRepository,
			WorkspaceMemberRepository workspaceMemberRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ApplicationEventPublisher eventPublisher) {
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.userRepository = userRepository;
		this.workspaceMemberRepository = workspaceMemberRepository;
		this.workspaceRepository = workspaceRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public DirectConversationResponse createOrGetConversation(
			UUID workspaceId, UUID recipientId, String principalName) {
		var actorMembership = workspaceAuthorizationService.requireMember(workspaceId, principalName);
		var workspace = workspaceRepository.findByIdForUpdate(workspaceId)
				.orElseThrow(com.example.demo.exception.WorkspaceNotFoundException::new);
		User actor = actorMembership.getUser();
		if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, actor.getId())) {
			throw new AccessDeniedException("Direct conversations are limited to workspace members.");
		}
		User recipient = userRepository.findById(recipientId)
				.filter(User::isActive)
				.orElseThrow(UserNotFoundException::new);
		if (actor.getId().equals(recipient.getId())) {
			throw new IllegalArgumentException("You cannot start a direct conversation with yourself.");
		}
		if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, recipient.getId())) {
			throw new AccessDeniedException("Direct conversations are limited to workspace members.");
		}

		var ids = List.of(actor.getId(), recipient.getId()).stream()
				.sorted(Comparator.comparing(UUID::toString))
				.toList();
		DirectConversation conversation = conversationRepository
				.findByWorkspace_IdAndParticipantOne_IdAndParticipantTwo_Id(workspaceId, ids.get(0), ids.get(1))
				.orElseGet(() -> {
					DirectConversation created = new DirectConversation();
					created.setWorkspace(workspace);
					created.setParticipantOne(actor.getId().equals(ids.get(0)) ? actor : recipient);
					created.setParticipantTwo(actor.getId().equals(ids.get(1)) ? actor : recipient);
					return conversationRepository.saveAndFlush(created);
				});
		return DirectConversationResponse.from(conversation, actor.getId());
	}

	@Transactional(readOnly = true)
	public List<DirectConversationResponse> list(UUID workspaceId, String principalName) {
		var membership = workspaceAuthorizationService.requireMember(workspaceId, principalName);
		return conversationRepository.findForMember(workspaceId, membership.getUser().getId()).stream()
				.map(conversation -> DirectConversationResponse.from(conversation, membership.getUser().getId()))
				.toList();
	}

	@Transactional(readOnly = true)
	public Page<DirectMessageResponse> history(UUID conversationId, String principalName, int page, int size) {
		validatePage(page, size);
		DirectConversation conversation = loadAuthorizedConversation(conversationId, principalName);
		return messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(
				conversation.getId(), PageRequest.of(page, size)).map(DirectMessageResponse::from);
	}

	@Transactional
	public DirectMessageResponse send(UUID conversationId, String principalName, ChatMessageRequest request) {
		DirectConversation conversation = loadAuthorizedConversation(conversationId, principalName);
		User sender = workspaceAuthorizationService.findActiveUser(principalName);
		DirectMessage message = new DirectMessage();
		message.setConversation(conversation);
		message.setSender(sender);
		message.setContent(request.content().trim());
		DirectMessageResponse response = DirectMessageResponse.from(messageRepository.saveAndFlush(message));
		conversation.touch();
		eventPublisher.publishEvent(new DirectMessageCreatedEvent(conversationId, response));
		return response;
	}

	@Transactional(readOnly = true)
	public void authorizeConversation(UUID conversationId, String principalName) {
		loadAuthorizedConversation(conversationId, principalName);
	}

	private DirectConversation loadAuthorizedConversation(UUID conversationId, String principalName) {
		DirectConversation conversation = conversationRepository.findDetailedById(conversationId)
				.orElseThrow(DirectConversationNotFoundException::new);
		var membership = workspaceAuthorizationService.requireMember(
				conversation.getWorkspace().getId(), principalName);
		UUID userId = membership.getUser().getId();
		if (!conversation.getParticipantOne().getId().equals(userId)
				&& !conversation.getParticipantTwo().getId().equals(userId)) {
			throw new AccessDeniedException("You do not have access to this conversation.");
		}
		if (!conversation.getParticipantOne().isActive() || !conversation.getParticipantTwo().isActive()) {
			throw new AccessDeniedException("Direct conversations require active workspace members.");
		}
		return conversation;
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
	}
}

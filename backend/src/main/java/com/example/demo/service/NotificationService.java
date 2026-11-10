package com.example.demo.service;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
	private final NotificationRepository notificationRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;

	public NotificationService(
			NotificationRepository notificationRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService) {
		this.notificationRepository = notificationRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
	}

	@Transactional(readOnly = true)
	public Page<NotificationResponse> list(String principalName, int page, int size) {
		User recipient = workspaceAuthorizationService.findActiveUser(principalName);
		validatePage(page, size);
		PageRequest pageable = PageRequest.of(
				page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
		return notificationRepository
				.findByRecipient_IdOrderByCreatedAtDescIdDesc(recipient.getId(), pageable)
				.map(NotificationResponse::from);
	}

	@Transactional(readOnly = true)
	public long unreadCount(String principalName) {
		User recipient = workspaceAuthorizationService.findActiveUser(principalName);
		return notificationRepository.countByRecipient_IdAndReadIsFalse(recipient.getId());
	}

	@Transactional
	public NotificationResponse markRead(UUID notificationId, String principalName, boolean read) {
		Notification notification = findOwned(notificationId, principalName);
		if (notification.isRead() != read) {
			notification.setRead(read);
			notification.setReadAt(read ? LocalDateTime.now(Clock.systemUTC()) : null);
			notification = notificationRepository.save(notification);
		}
		return NotificationResponse.from(notification);
	}

	@Transactional
	public int markAllRead(String principalName) {
		User recipient = workspaceAuthorizationService.findActiveUser(principalName);
		return notificationRepository.markAllRead(
				recipient.getId(), LocalDateTime.now(Clock.systemUTC()));
	}

	@Transactional
	public void delete(UUID notificationId, String principalName) {
		notificationRepository.delete(findOwned(notificationId, principalName));
	}

	private Notification findOwned(UUID notificationId, String principalName) {
		User recipient = workspaceAuthorizationService.findActiveUser(principalName);
		return notificationRepository.findByIdAndRecipient_Id(notificationId, recipient.getId())
				.orElseThrow(com.example.demo.exception.NotificationNotFoundException::new);
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
	}
}

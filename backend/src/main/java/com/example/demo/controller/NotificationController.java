package com.example.demo.controller;

import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.service.NotificationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public Page<NotificationResponse> list(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return notificationService.list(authentication.getName(), page, size);
	}

	@GetMapping("/unread-count")
	public Map<String, Long> unreadCount(Authentication authentication) {
		return Map.of("count", notificationService.unreadCount(authentication.getName()));
	}

	@PatchMapping("/{notificationId}/read")
	public NotificationResponse markRead(
			Authentication authentication, @PathVariable UUID notificationId) {
		return notificationService.markRead(notificationId, authentication.getName(), true);
	}

	@PatchMapping("/{notificationId}/unread")
	public NotificationResponse markUnread(
			Authentication authentication, @PathVariable UUID notificationId) {
		return notificationService.markRead(notificationId, authentication.getName(), false);
	}

	@PatchMapping("/read-all")
	public Map<String, Integer> markAllRead(Authentication authentication) {
		return Map.of("updatedCount", notificationService.markAllRead(authentication.getName()));
	}

	@DeleteMapping("/{notificationId}")
	public ResponseEntity<Void> delete(
			Authentication authentication, @PathVariable UUID notificationId) {
		notificationService.delete(notificationId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

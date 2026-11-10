package com.example.demo.repository;

import com.example.demo.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
	@Transactional
	void deleteAllByRecipient_Id(UUID recipientId);

	@EntityGraph(attributePaths = {"actor", "workspace", "project", "task", "taskComment", "chatMessage"})
	Page<Notification> findByRecipient_IdOrderByCreatedAtDescIdDesc(UUID recipientId, Pageable pageable);

	@EntityGraph(attributePaths = {"actor", "workspace", "project", "task", "taskComment", "chatMessage"})
	Optional<Notification> findByIdAndRecipient_Id(UUID notificationId, UUID recipientId);

	long countByRecipient_IdAndReadIsFalse(UUID recipientId);

	boolean existsByRecipient_IdAndEventId(UUID recipientId, UUID eventId);

	@Modifying
	@Query("""
			update Notification notification
			set notification.read = true, notification.readAt = :readAt
			where notification.recipient.id = :recipientId and notification.read = false
			""")
	int markAllRead(
			@Param("recipientId") UUID recipientId,
			@Param("readAt") LocalDateTime readAt);
}

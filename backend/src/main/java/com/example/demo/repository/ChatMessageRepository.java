package com.example.demo.repository;

import com.example.demo.entity.ChatMessage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
	long countByProject_Id(UUID projectId);

	@EntityGraph(attributePaths = {"project", "sender"})
	Page<ChatMessage> findByProject_IdOrderByCreatedAtAscIdAsc(UUID projectId, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select message from ChatMessage message where message.id = :messageId")
	Optional<ChatMessage> findByIdForUpdate(@Param("messageId") UUID messageId);
}

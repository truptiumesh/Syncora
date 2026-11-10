package com.example.demo.repository;

import com.example.demo.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
	@EntityGraph(attributePaths = {"conversation", "sender"})
	Page<DirectMessage> findByConversation_IdOrderByCreatedAtAscIdAsc(UUID conversationId, Pageable pageable);

	long countByConversation_Id(UUID conversationId);
}

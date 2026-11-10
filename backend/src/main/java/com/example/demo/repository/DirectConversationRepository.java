package com.example.demo.repository;

import com.example.demo.entity.DirectConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectConversationRepository extends JpaRepository<DirectConversation, UUID> {
	@EntityGraph(attributePaths = {"workspace", "participantOne", "participantTwo"})
	Optional<DirectConversation> findByWorkspace_IdAndParticipantOne_IdAndParticipantTwo_Id(
			UUID workspaceId, UUID participantOneId, UUID participantTwoId);

	@EntityGraph(attributePaths = {"workspace", "participantOne", "participantTwo"})
	@Query("""
			select c from DirectConversation c
			where c.workspace.id = :workspaceId
			  and (c.participantOne.id = :userId or c.participantTwo.id = :userId)
			order by c.updatedAt desc, c.id desc
			""")
	List<DirectConversation> findForMember(
			@Param("workspaceId") UUID workspaceId,
			@Param("userId") UUID userId);

	@EntityGraph(attributePaths = {"workspace", "participantOne", "participantTwo"})
	@Query("select c from DirectConversation c where c.id = :conversationId")
	Optional<DirectConversation> findDetailedById(@Param("conversationId") UUID conversationId);
}

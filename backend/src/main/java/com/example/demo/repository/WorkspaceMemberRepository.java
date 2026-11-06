package com.example.demo.repository;

import com.example.demo.entity.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {
	Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);

	Optional<WorkspaceMember> findByIdAndWorkspace_Id(UUID id, UUID workspaceId);

	List<WorkspaceMember> findByWorkspace_IdOrderByJoinedAtAsc(UUID workspaceId);

	List<WorkspaceMember> findByUser_IdOrderByWorkspace_CreatedAtDesc(UUID userId);

	boolean existsByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);

	long countByWorkspace_Id(UUID workspaceId);

	long deleteByWorkspace_Id(UUID workspaceId);
}

package com.example.demo.repository;

import com.example.demo.entity.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
	Optional<Workspace> findByName(String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Workspace w where w.id = :workspaceId")
	Optional<Workspace> findByIdForUpdate(@Param("workspaceId") UUID workspaceId);
}

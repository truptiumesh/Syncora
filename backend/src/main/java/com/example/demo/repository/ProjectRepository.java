package com.example.demo.repository;

import com.example.demo.entity.Project;
import com.example.demo.enums.ProjectStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
	boolean existsByWorkspace_IdAndKey(UUID workspaceId, String key);

	Optional<Project> findByWorkspace_IdAndName(UUID workspaceId, String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Project p where p.id = :projectId")
	Optional<Project> findByIdForUpdate(@Param("projectId") UUID projectId);

	@Query("""
			select p from Project p
			where p.workspace.id = :workspaceId
			  and (:workspaceAdmin = true or exists (
			      select pm.id from ProjectMember pm
			      where pm.project = p and pm.user.id = :userId
			  ))
			  and (:status is null or p.status = :status)
			  and ((:archived = true and p.archivedAt is not null)
			       or (:archived = false and p.archivedAt is null))
			  and (coalesce(:search, '') = ''
			       or lower(p.name) like lower(concat('%', :search, '%'))
			       or lower(p.key) like lower(concat('%', :search, '%')))
			""")
	Page<Project> searchVisible(
			@Param("workspaceId") UUID workspaceId,
			@Param("userId") UUID userId,
			@Param("workspaceAdmin") boolean workspaceAdmin,
			@Param("status") ProjectStatus status,
			@Param("search") String search,
			@Param("archived") boolean archived,
			Pageable pageable);
}

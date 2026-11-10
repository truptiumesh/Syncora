package com.example.demo.repository;

import com.example.demo.entity.FileAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, UUID> {
	@EntityGraph(attributePaths = {"uploader", "workspace", "project"})
	Page<FileAttachment> findByWorkspace_IdAndProjectIsNullOrderByUploadedAtDescIdDesc(
			UUID workspaceId, Pageable pageable);

	@EntityGraph(attributePaths = {"uploader", "workspace", "project"})
	Page<FileAttachment> findByProject_IdOrderByUploadedAtDescIdDesc(UUID projectId, Pageable pageable);

	@EntityGraph(attributePaths = {"uploader", "workspace", "project"})
	Optional<FileAttachment> findWithOwnerById(UUID id);

	@Query("select attachment.storageKey from FileAttachment attachment where attachment.workspace.id = :workspaceId")
	List<String> findStorageKeysByWorkspaceId(@Param("workspaceId") UUID workspaceId);

	@Query("select attachment.storageKey from FileAttachment attachment where attachment.project.id = :projectId")
	List<String> findStorageKeysByProjectId(@Param("projectId") UUID projectId);
}

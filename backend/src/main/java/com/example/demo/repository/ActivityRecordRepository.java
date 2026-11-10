package com.example.demo.repository;

import com.example.demo.entity.ActivityRecord;
import com.example.demo.enums.WorkspaceRole;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, UUID> {
	boolean existsByEventId(UUID eventId);
	long countByWorkspace_Id(UUID workspaceId);

	@EntityGraph(attributePaths = {"actor", "workspace", "project", "task"})
	@Query("""
			select activity from ActivityRecord activity
			where exists (
				select member.id from WorkspaceMember member
				where member.workspace.id = activity.workspace.id and member.user.id = :userId
			)
			and (
				activity.project is null
				or exists (
					select projectMember.id from ProjectMember projectMember
					where projectMember.project.id = activity.project.id
						and projectMember.user.id = :userId
				)
				or exists (
					select adminMember.id from WorkspaceMember adminMember
					where adminMember.workspace.id = activity.workspace.id
						and adminMember.user.id = :userId
						and adminMember.role in :workspaceAdminRoles
				)
			)
			""")
	Page<ActivityRecord> findVisibleToUser(
			@Param("userId") UUID userId,
			@Param("workspaceAdminRoles") Set<WorkspaceRole> workspaceAdminRoles,
			Pageable pageable);

	@EntityGraph(attributePaths = {"actor", "workspace", "project", "task"})
	@Query("""
			select activity from ActivityRecord activity
			where activity.workspace.id = :workspaceId
				and exists (
					select member.id from WorkspaceMember member
					where member.workspace.id = activity.workspace.id and member.user.id = :userId
				)
				and (
					activity.project is null
					or exists (
						select projectMember.id from ProjectMember projectMember
						where projectMember.project.id = activity.project.id
							and projectMember.user.id = :userId
					)
					or exists (
						select adminMember.id from WorkspaceMember adminMember
						where adminMember.workspace.id = activity.workspace.id
							and adminMember.user.id = :userId
							and adminMember.role in :workspaceAdminRoles
					)
				)
			""")
	Page<ActivityRecord> findVisibleToUserInWorkspace(
			@Param("userId") UUID userId,
			@Param("workspaceId") UUID workspaceId,
			@Param("workspaceAdminRoles") Set<WorkspaceRole> workspaceAdminRoles,
			Pageable pageable);

	@EntityGraph(attributePaths = {"actor", "workspace", "project", "task"})
	Page<ActivityRecord> findByProject_IdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable);
}

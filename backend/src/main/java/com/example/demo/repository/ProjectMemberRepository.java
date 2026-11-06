package com.example.demo.repository;

import com.example.demo.entity.ProjectMember;
import com.example.demo.enums.ProjectRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
	Optional<ProjectMember> findByProject_IdAndUser_Id(UUID projectId, UUID userId);

	Optional<ProjectMember> findByIdAndProject_Id(UUID memberId, UUID projectId);

	List<ProjectMember> findByProject_IdOrderByJoinedAtAsc(UUID projectId);

	boolean existsByProject_IdAndUser_Id(UUID projectId, UUID userId);

	long countByProject_Id(UUID projectId);

	long countByProject_IdAndRole(UUID projectId, ProjectRole role);

	Optional<ProjectMember> findByProject_IdAndRole(UUID projectId, ProjectRole role);

	@Query("""
			select pm.project.id as projectId, pm.user.fullName as fullName
			from ProjectMember pm
			where pm.project.id in :projectIds and pm.role = :role
			""")
	List<ManagerName> findManagerNames(
			@Param("projectIds") Collection<UUID> projectIds,
			@Param("role") ProjectRole role);

	@Query("""
			select pm.project.id as projectId, count(pm.id) as memberCount
			from ProjectMember pm
			where pm.project.id in :projectIds
			group by pm.project.id
			""")
	List<MemberCount> countMembersByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

	@Query("""
			select pm.project.id as projectId, pm.role as role
			from ProjectMember pm
			where pm.project.id in :projectIds and pm.user.id = :userId
			""")
	List<UserProjectRole> findRolesForUser(
			@Param("projectIds") Collection<UUID> projectIds,
			@Param("userId") UUID userId);

	interface ManagerName {
		UUID getProjectId();
		String getFullName();
	}

	interface MemberCount {
		UUID getProjectId();
		Long getMemberCount();
	}

	interface UserProjectRole {
		UUID getProjectId();
		ProjectRole getRole();
	}
}

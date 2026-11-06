package com.example.demo.repository;

import com.example.demo.entity.Task;
import com.example.demo.enums.TaskPriority;
import com.example.demo.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {
	boolean existsByProject_IdAndTaskKey(UUID projectId, String taskKey);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Optional<Task> findById(UUID id);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	Optional<Task> findByIdAndProject_Id(UUID id, UUID projectId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Task t where t.id = :taskId")
	Optional<Task> findByIdForUpdate(@Param("taskId") UUID taskId);

	@EntityGraph(attributePaths = {"project", "assignee", "createdBy"})
	@Query("""
			select t from Task t
			where t.project.id = :projectId
			  and (:status is null or t.status = :status)
			  and (:priority is null or t.priority = :priority)
			  and (:assigneeId is null or t.assignee.id = :assigneeId)
			  and (:overdueOnly = false or (t.dueDate < :today and t.status <> :doneStatus))
			  and (:search = ''
			       or lower(t.title) like lower(concat('%', :search, '%'))
			       or lower(coalesce(t.description, '')) like lower(concat('%', :search, '%')))
			""")
	Page<Task> searchProjectTasks(
			@Param("projectId") UUID projectId,
			@Param("status") TaskStatus status,
			@Param("priority") TaskPriority priority,
			@Param("assigneeId") UUID assigneeId,
			@Param("overdueOnly") boolean overdueOnly,
			@Param("today") LocalDate today,
			@Param("doneStatus") TaskStatus doneStatus,
			@Param("search") String search,
			Pageable pageable);

	@Query("""
			select t.id as taskId, count(c.id) as commentCount
			from Task t left join TaskComment c on c.task = t
			where t.id in :taskIds
			group by t.id
			""")
	List<CommentCount> countCommentsByTaskIds(@Param("taskIds") Collection<UUID> taskIds);

	interface CommentCount {
		UUID getTaskId();
		Long getCommentCount();
	}
}

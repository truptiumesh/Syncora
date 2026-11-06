package com.example.demo.repository;

import com.example.demo.entity.TaskComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
	long countByTask_Id(UUID taskId);

	@EntityGraph(attributePaths = {"task", "author"})
	List<TaskComment> findByTask_IdOrderByCreatedAtAsc(UUID taskId);

	@EntityGraph(attributePaths = {"task", "author"})
	Optional<TaskComment> findByIdAndTask_Id(UUID commentId, UUID taskId);
}

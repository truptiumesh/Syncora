package com.example.demo.service;

import com.example.demo.dto.request.TaskCommentRequest;
import com.example.demo.dto.response.TaskCommentResponse;
import com.example.demo.entity.TaskComment;
import com.example.demo.entity.User;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.exception.TaskCommentNotFoundException;
import com.example.demo.repository.TaskCommentRepository;
import com.example.demo.service.ProjectAuthorizationService.Access;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommentService {
	private final TaskCommentRepository taskCommentRepository;
	private final TaskAuthorizationService taskAuthorizationService;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;

	public TaskCommentService(
			TaskCommentRepository taskCommentRepository,
			TaskAuthorizationService taskAuthorizationService,
			WorkspaceAuthorizationService workspaceAuthorizationService) {
		this.taskCommentRepository = taskCommentRepository;
		this.taskAuthorizationService = taskAuthorizationService;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
	}

	@Transactional(readOnly = true)
	public List<TaskCommentResponse> list(UUID taskId, String principalName) {
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		return taskCommentRepository.findByTask_IdOrderByCreatedAtAsc(taskId).stream()
				.map(TaskCommentResponse::from)
				.toList();
	}

	@Transactional
	public TaskCommentResponse create(
			UUID taskId, String principalName, TaskCommentRequest request) {
		TaskAuthorizationService.Access access =
				taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(access.task().getProject());
		User author = workspaceAuthorizationService.findActiveUser(principalName);
		TaskComment comment = new TaskComment();
		comment.setTask(access.task());
		comment.setAuthor(author);
		comment.setContent(request.content().trim());
		return TaskCommentResponse.from(taskCommentRepository.save(comment));
	}

	@Transactional
	public TaskCommentResponse update(
			UUID taskId,
			UUID commentId,
			String principalName,
			TaskCommentRequest request) {
		TaskAuthorizationService.Access access =
				taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(access.task().getProject());
		TaskComment comment = findComment(taskId, commentId);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		requireOwnerOrModerator(comment, actor, access.projectAccess());
		comment.setContent(request.content().trim());
		return TaskCommentResponse.from(taskCommentRepository.save(comment));
	}

	@Transactional
	public void delete(UUID taskId, UUID commentId, String principalName) {
		TaskAuthorizationService.Access access =
				taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(access.task().getProject());
		TaskComment comment = findComment(taskId, commentId);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		requireOwnerOrModerator(comment, actor, access.projectAccess());
		taskCommentRepository.delete(comment);
	}

	private TaskComment findComment(UUID taskId, UUID commentId) {
		return taskCommentRepository.findByIdAndTask_Id(commentId, taskId)
				.orElseThrow(TaskCommentNotFoundException::new);
	}

	private void requireOwnerOrModerator(TaskComment comment, User actor, Access projectAccess) {
		if (comment.getAuthor().getId().equals(actor.getId())) {
			return;
		}
		boolean workspaceModerator = projectAccess.workspaceMember().getRole() == WorkspaceRole.OWNER
				|| projectAccess.workspaceMember().getRole() == WorkspaceRole.ADMIN;
		boolean projectManager = projectAccess.projectMember() != null
				&& projectAccess.projectMember().getRole() == ProjectRole.PROJECT_MANAGER;
		if (!workspaceModerator && !projectManager) {
			throw new AccessDeniedException("Only the comment author or a project manager can change this comment.");
		}
	}

	private void ensureProjectActive(com.example.demo.entity.Project project) {
		if (project.getArchivedAt() != null) {
			throw new IllegalArgumentException("Comments cannot be changed while the project is archived.");
		}
	}
}

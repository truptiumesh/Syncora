package com.example.demo.service;

import com.example.demo.dto.request.AssignTaskRequest;
import com.example.demo.dto.request.CreateTaskRequest;
import com.example.demo.dto.request.UpdateTaskPriorityRequest;
import com.example.demo.dto.request.UpdateTaskRequest;
import com.example.demo.dto.request.UpdateTaskStatusRequest;
import com.example.demo.dto.response.TaskResponse;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.enums.TaskPriority;
import com.example.demo.enums.TaskStatus;
import com.example.demo.exception.InvalidTaskAssigneeException;
import com.example.demo.exception.ProjectNotFoundException;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskCommentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TaskAuthorizationService.Access;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
	private final TaskRepository taskRepository;
	private final TaskCommentRepository taskCommentRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final UserRepository userRepository;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ProjectAuthorizationService projectAuthorizationService;
	private final TaskAuthorizationService taskAuthorizationService;

	public TaskService(
			TaskRepository taskRepository,
			TaskCommentRepository taskCommentRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			UserRepository userRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ProjectAuthorizationService projectAuthorizationService,
			TaskAuthorizationService taskAuthorizationService) {
		this.taskRepository = taskRepository;
		this.taskCommentRepository = taskCommentRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.userRepository = userRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.projectAuthorizationService = projectAuthorizationService;
		this.taskAuthorizationService = taskAuthorizationService;
	}

	@Transactional
	public TaskResponse create(UUID projectId, String principalName, CreateTaskRequest request) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
		Project project = projectRepository.findByIdForUpdate(projectId)
				.orElseThrow(ProjectNotFoundException::new);
		ensureProjectActive(project);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		User assignee = resolveAssignee(projectId, request.assigneeId());

		Task task = new Task();
		task.setProject(project);
		task.setTitle(request.title().trim());
		task.setDescription(normalizeOptional(request.description()));
		task.setTaskKey(nextTaskKey(project));
		task.setStatus(TaskStatus.TODO);
		task.setPriority(request.priority());
		task.setAssignee(assignee);
		task.setCreatedBy(actor);
		task.setDueDate(request.dueDate());
		task = taskRepository.saveAndFlush(task);
		return TaskResponse.from(task, 0);
	}

	@Transactional(readOnly = true)
	public Page<TaskResponse> list(
			UUID projectId,
			String principalName,
			String search,
			TaskStatus status,
			TaskPriority priority,
			UUID assigneeId,
			boolean overdueOnly,
			int page,
			int size,
			String sortBy,
			String sortDirection) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
		if (assigneeId != null
				&& !projectMemberRepository.existsByProject_IdAndUser_Id(projectId, assigneeId)) {
			throw new InvalidTaskAssigneeException();
		}
		String query = search == null || search.isBlank() ? "" : search.trim();
		String normalizedDirection = sortDirection == null ? "DESC" : sortDirection.trim().toUpperCase(Locale.ROOT);
		Sort.Direction direction;
		try {
			direction = Sort.Direction.valueOf(normalizedDirection);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Sort direction must be ASC or DESC.");
		}
		String property = sortProperty(sortBy);
		Pageable pageable = PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by("id")));
		Page<Task> tasks = taskRepository.searchProjectTasks(
				projectId, status, priority, assigneeId, overdueOnly,
				LocalDate.now(Clock.systemUTC()), TaskStatus.DONE, query, pageable);
		if (tasks.isEmpty()) {
			return tasks.map(task -> TaskResponse.from(task, 0));
		}
		Collection<UUID> taskIds = tasks.getContent().stream().map(Task::getId).toList();
		Map<UUID, Long> counts = taskRepository.countCommentsByTaskIds(taskIds).stream()
				.collect(Collectors.toMap(
						TaskRepository.CommentCount::getTaskId,
						TaskRepository.CommentCount::getCommentCount));
		return tasks.map(task -> TaskResponse.from(task, counts.getOrDefault(task.getId(), 0L)));
	}

	@Transactional(readOnly = true)
	public TaskResponse get(UUID taskId, String principalName) {
		Access access = taskAuthorizationService.requireTaskAccess(taskId, principalName);
		return TaskResponse.from(access.task(), taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse update(UUID taskId, String principalName, UpdateTaskRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		task.setTitle(request.title().trim());
		task.setDescription(normalizeOptional(request.description()));
		task.setDueDate(request.dueDate());
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse updateStatus(
			UUID taskId, String principalName, UpdateTaskStatusRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		task.setStatus(request.status());
		task.setCompletedAt(request.status() == TaskStatus.DONE
				? (task.getCompletedAt() == null ? LocalDateTime.now(Clock.systemUTC()) : task.getCompletedAt())
				: null);
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse updatePriority(
			UUID taskId, String principalName, UpdateTaskPriorityRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		task.setPriority(request.priority());
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse assign(
			UUID taskId, String principalName, AssignTaskRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		task.setAssignee(resolveAssignee(task.getProject().getId(), request.assigneeId()));
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public void delete(UUID taskId, String principalName) {
		Task task = lockTask(taskId);
		projectAuthorizationService.requireProjectManagerOrWorkspaceAdmin(
				task.getProject().getId(), principalName);
		ensureProjectActive(task.getProject());
		taskCommentRepository.deleteAll(
				taskCommentRepository.findByTask_IdOrderByCreatedAtAsc(taskId));
		taskRepository.delete(task);
	}

	private Task lockTask(UUID taskId) {
		return taskRepository.findByIdForUpdate(taskId).orElseThrow(TaskNotFoundException::new);
	}

	private User resolveAssignee(UUID projectId, UUID assigneeId) {
		if (assigneeId == null) {
			return null;
		}
		return userRepository.findById(assigneeId)
				.filter(User::isActive)
				.filter(user -> projectMemberRepository.existsByProject_IdAndUser_Id(projectId, user.getId()))
				.orElseThrow(InvalidTaskAssigneeException::new);
	}

	private String nextTaskKey(Project project) {
		String base = project.getKey() + "-";
		int sequence = 1;
		String candidate;
		do {
			candidate = base + sequence++;
		} while (taskRepository.existsByProject_IdAndTaskKey(project.getId(), candidate));
		return candidate;
	}

	private String sortProperty(String sortBy) {
		String field = sortBy == null ? "createdAt" : sortBy.trim();
		return switch (field) {
			case "createdAt" -> "createdAt";
			case "dueDate" -> "dueDate";
			case "priority" -> "priority";
			case "updatedAt" -> "updatedAt";
			default -> throw new IllegalArgumentException(
					"Sort field must be createdAt, dueDate, priority, or updatedAt.");
		};
	}

	private void ensureProjectActive(Project project) {
		if (project.getArchivedAt() != null) {
			throw new IllegalArgumentException("Tasks cannot be changed while the project is archived.");
		}
	}

	private String normalizeOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

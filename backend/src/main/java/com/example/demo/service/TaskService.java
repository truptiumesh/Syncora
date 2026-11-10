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
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher eventPublisher;

	public TaskService(
			TaskRepository taskRepository,
			TaskCommentRepository taskCommentRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			UserRepository userRepository,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ProjectAuthorizationService projectAuthorizationService,
			TaskAuthorizationService taskAuthorizationService,
			ApplicationEventPublisher eventPublisher) {
		this.taskRepository = taskRepository;
		this.taskCommentRepository = taskCommentRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.userRepository = userRepository;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.projectAuthorizationService = projectAuthorizationService;
		this.taskAuthorizationService = taskAuthorizationService;
		this.eventPublisher = eventPublisher;
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
		List<ActivityEvent.NotificationTarget> notifications = assignee == null ? List.of() : List.of(
				new ActivityEvent.NotificationTarget(
						assignee.getId(),
						NotificationType.TASK_ASSIGNED,
						"Task assigned",
						"You were assigned to task " + task.getTaskKey() + "."));
		publishActivity(
				actor, task, ActivityType.TASK_CREATED,
				actor.getFullName() + " created task " + task.getTaskKey() + ".",
				notifications);
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
		String previousTitle = task.getTitle();
		String previousDescription = task.getDescription();
		LocalDate previousDueDate = task.getDueDate();
		task.setTitle(request.title().trim());
		task.setDescription(normalizeOptional(request.description()));
		task.setDueDate(request.dueDate());
		if (!Objects.equals(previousTitle, task.getTitle())
				|| !Objects.equals(previousDescription, task.getDescription())
				|| !Objects.equals(previousDueDate, task.getDueDate())) {
			User actor = workspaceAuthorizationService.findActiveUser(principalName);
			publishActivity(
					actor, task, ActivityType.TASK_UPDATED,
					actor.getFullName() + " updated task " + task.getTaskKey() + ".",
					List.of());
		}
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse updateStatus(
			UUID taskId, String principalName, UpdateTaskStatusRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		TaskStatus previousStatus = task.getStatus();
		task.setStatus(request.status());
		task.setCompletedAt(request.status() == TaskStatus.DONE
				? (task.getCompletedAt() == null ? LocalDateTime.now(Clock.systemUTC()) : task.getCompletedAt())
				: null);
		if (previousStatus != task.getStatus()) {
			User actor = workspaceAuthorizationService.findActiveUser(principalName);
			List<ActivityEvent.NotificationTarget> notifications = task.getAssignee() == null
					? List.of()
					: List.of(new ActivityEvent.NotificationTarget(
							task.getAssignee().getId(),
							NotificationType.TASK_STATUS_CHANGED,
							"Task status changed",
							"Task " + task.getTaskKey() + " is now " + task.getStatus() + "."));
			publishActivity(
					actor, task, ActivityType.TASK_STATUS_CHANGED,
					actor.getFullName() + " changed task " + task.getTaskKey()
							+ " to " + task.getStatus() + ".",
					notifications);
		}
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse updatePriority(
			UUID taskId, String principalName, UpdateTaskPriorityRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		TaskPriority previousPriority = task.getPriority();
		task.setPriority(request.priority());
		if (previousPriority != task.getPriority()) {
			User actor = workspaceAuthorizationService.findActiveUser(principalName);
			List<ActivityEvent.NotificationTarget> notifications = task.getAssignee() == null
					? List.of()
					: List.of(new ActivityEvent.NotificationTarget(
							task.getAssignee().getId(),
							NotificationType.TASK_PRIORITY_CHANGED,
							"Task priority changed",
							"Task " + task.getTaskKey() + " priority is now " + task.getPriority() + "."));
			publishActivity(
					actor, task, ActivityType.TASK_PRIORITY_CHANGED,
					actor.getFullName() + " changed task " + task.getTaskKey()
							+ " priority to " + task.getPriority() + ".",
					notifications);
		}
		return TaskResponse.from(task, taskCommentRepository.countByTask_Id(taskId));
	}

	@Transactional
	public TaskResponse assign(
			UUID taskId, String principalName, AssignTaskRequest request) {
		Task task = lockTask(taskId);
		taskAuthorizationService.requireTaskAccess(taskId, principalName);
		ensureProjectActive(task.getProject());
		User previousAssignee = task.getAssignee();
		User newAssignee = resolveAssignee(task.getProject().getId(), request.assigneeId());
		task.setAssignee(newAssignee);
		if (!Objects.equals(
				previousAssignee == null ? null : previousAssignee.getId(),
				newAssignee == null ? null : newAssignee.getId())) {
			User actor = workspaceAuthorizationService.findActiveUser(principalName);
			ActivityType activityType = previousAssignee == null
					? ActivityType.TASK_ASSIGNED
					: ActivityType.TASK_REASSIGNED;
			List<ActivityEvent.NotificationTarget> notifications = newAssignee == null
					? List.of()
					: List.of(new ActivityEvent.NotificationTarget(
							newAssignee.getId(),
							previousAssignee == null
									? NotificationType.TASK_ASSIGNED
									: NotificationType.TASK_REASSIGNED,
							previousAssignee == null ? "Task assigned" : "Task reassigned",
							"You were assigned to task " + task.getTaskKey() + "."));
			publishActivity(
					actor, task, activityType,
					actor.getFullName() + (previousAssignee == null ? " assigned " : " reassigned ")
							+ "task " + task.getTaskKey() + ".",
					notifications);
		}
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

	private void publishActivity(
			User actor,
			Task task,
			ActivityType type,
			String description,
			List<ActivityEvent.NotificationTarget> notifications) {
		eventPublisher.publishEvent(ActivityEvent.create(
				actor,
				type,
				description,
				task.getProject().getWorkspace().getId(),
				task.getProject().getId(),
				task.getId(),
				null,
				null,
				notifications));
	}
}

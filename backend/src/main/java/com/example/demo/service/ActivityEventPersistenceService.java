package com.example.demo.service;

import com.example.demo.entity.ActivityRecord;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Project;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskComment;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.repository.ActivityRecordRepository;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskCommentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.enums.WorkspaceRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityEventPersistenceService {
	private final ActivityRecordRepository activityRepository;
	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final TaskCommentRepository taskCommentRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final WorkspaceMemberRepository workspaceMemberRepository;
	private final ProjectMemberRepository projectMemberRepository;

	public ActivityEventPersistenceService(
			ActivityRecordRepository activityRepository,
			NotificationRepository notificationRepository,
			UserRepository userRepository,
			WorkspaceRepository workspaceRepository,
			ProjectRepository projectRepository,
			TaskRepository taskRepository,
			TaskCommentRepository taskCommentRepository,
			ChatMessageRepository chatMessageRepository,
			WorkspaceMemberRepository workspaceMemberRepository,
			ProjectMemberRepository projectMemberRepository) {
		this.activityRepository = activityRepository;
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.workspaceRepository = workspaceRepository;
		this.projectRepository = projectRepository;
		this.taskRepository = taskRepository;
		this.taskCommentRepository = taskCommentRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.workspaceMemberRepository = workspaceMemberRepository;
		this.projectMemberRepository = projectMemberRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<NotificationDelivery> persist(ActivityEvent event) {
		if (activityRepository.existsByEventId(event.eventId())) {
			return List.of();
		}

		User actor = userRepository.findById(event.actorId()).orElse(null);
		Workspace workspace = workspaceRepository.findById(event.workspaceId()).orElse(null);
		if (workspace == null) {
			return List.of();
		}
		Project project = findProject(event.projectId());
		if (event.projectId() != null && project == null) {
			return List.of();
		}
		Task task = findTask(event.taskId());

		ActivityRecord activity = new ActivityRecord();
		activity.setEventId(event.eventId());
		activity.setActor(actor);
		activity.setType(event.activityType());
		activity.setDescription(limit(event.description(), 500));
		activity.setWorkspace(workspace);
		activity.setProject(project);
		activity.setTask(task);
		activityRepository.save(activity);

		Map<UUID, ActivityEvent.NotificationTarget> uniqueTargets = new LinkedHashMap<>();
		for (ActivityEvent.NotificationTarget target : event.notifications()) {
			if (!target.recipientId().equals(event.actorId())) {
				uniqueTargets.putIfAbsent(target.recipientId(), target);
			}
		}

		List<NotificationDelivery> saved = new ArrayList<>();
		for (ActivityEvent.NotificationTarget target : uniqueTargets.values()) {
			if (notificationRepository.existsByRecipient_IdAndEventId(target.recipientId(), event.eventId())) {
				continue;
			}
			User recipient = userRepository.findById(target.recipientId())
					.filter(User::isActive)
					.orElse(null);
			if (recipient == null || !canAccess(recipient, workspace, project)) {
				continue;
			}
			Notification notification = new Notification();
			notification.setEventId(event.eventId());
			notification.setRecipient(recipient);
			notification.setActor(actor);
			notification.setType(target.type());
			notification.setTitle(limit(target.title(), 120));
			notification.setMessage(limit(target.message(), 500));
			notification.setWorkspace(workspace);
			notification.setProject(project);
			notification.setTask(task);
			notification.setTaskComment(findComment(event.taskCommentId()));
			notification.setChatMessage(findChatMessage(event.chatMessageId()));
			saved.add(new NotificationDelivery(
					recipient.getEmail(),
					com.example.demo.dto.response.NotificationResponse.from(
							notificationRepository.saveAndFlush(notification))));
		}
		return saved;
	}

	private boolean canAccess(User recipient, Workspace workspace, Project project) {
		WorkspaceMember membership = workspaceMemberRepository
				.findByWorkspace_IdAndUser_Id(workspace.getId(), recipient.getId())
				.filter(member -> member.getUser().isActive())
				.orElse(null);
		if (membership == null) {
			return false;
		}
		return project == null
				|| membership.getRole() == WorkspaceRole.OWNER
				|| membership.getRole() == WorkspaceRole.ADMIN
				|| projectMemberRepository.existsByProject_IdAndUser_Id(project.getId(), recipient.getId());
	}

	private Project findProject(UUID projectId) {
		return projectId == null ? null : projectRepository.findById(projectId).orElse(null);
	}

	private Task findTask(UUID taskId) {
		return taskId == null ? null : taskRepository.findById(taskId).orElse(null);
	}

	private TaskComment findComment(UUID commentId) {
		return commentId == null ? null : taskCommentRepository.findById(commentId).orElse(null);
	}

	private com.example.demo.entity.ChatMessage findChatMessage(UUID chatMessageId) {
		return chatMessageId == null ? null : chatMessageRepository.findById(chatMessageId).orElse(null);
	}

	private String limit(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}

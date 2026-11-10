package com.example.demo.entity;

import com.example.demo.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private User recipient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 40)
	private NotificationType type;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, length = 500)
	private String message;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workspace_id")
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id")
	private Task task;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_comment_id")
	private TaskComment taskComment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_message_id")
	private ChatMessage chatMessage;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "read_at")
	private LocalDateTime readAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now(Clock.systemUTC());
	}

	public UUID getId() { return id; }
	public UUID getEventId() { return eventId; }
	public void setEventId(UUID eventId) { this.eventId = eventId; }
	public User getRecipient() { return recipient; }
	public void setRecipient(User recipient) { this.recipient = recipient; }
	public User getActor() { return actor; }
	public void setActor(User actor) { this.actor = actor; }
	public NotificationType getType() { return type; }
	public void setType(NotificationType type) { this.type = type; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }
	public Workspace getWorkspace() { return workspace; }
	public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
	public Project getProject() { return project; }
	public void setProject(Project project) { this.project = project; }
	public Task getTask() { return task; }
	public void setTask(Task task) { this.task = task; }
	public TaskComment getTaskComment() { return taskComment; }
	public void setTaskComment(TaskComment taskComment) { this.taskComment = taskComment; }
	public ChatMessage getChatMessage() { return chatMessage; }
	public void setChatMessage(ChatMessage chatMessage) { this.chatMessage = chatMessage; }
	public boolean isRead() { return read; }
	public void setRead(boolean read) { this.read = read; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getReadAt() { return readAt; }
	public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}

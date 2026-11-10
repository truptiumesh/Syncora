package com.example.demo.entity;

import com.example.demo.enums.ActivityType;
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
@Table(name = "activity_records")
public class ActivityRecord {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_id", nullable = false, unique = true, updatable = false)
	private UUID eventId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(name = "activity_type", nullable = false, length = 40)
	private ActivityType type;

	@Column(nullable = false, length = 500)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id")
	private Task task;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now(Clock.systemUTC());
	}

	public UUID getId() { return id; }
	public UUID getEventId() { return eventId; }
	public void setEventId(UUID eventId) { this.eventId = eventId; }
	public User getActor() { return actor; }
	public void setActor(User actor) { this.actor = actor; }
	public ActivityType getType() { return type; }
	public void setType(ActivityType type) { this.type = type; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public Workspace getWorkspace() { return workspace; }
	public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
	public Project getProject() { return project; }
	public void setProject(Project project) { this.project = project; }
	public Task getTask() { return task; }
	public void setTask(Task task) { this.task = task; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}

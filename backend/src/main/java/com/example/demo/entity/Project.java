package com.example.demo.entity;

import com.example.demo.enums.ProjectStatus;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects", uniqueConstraints = {
		@UniqueConstraint(name = "uk_projects_workspace_key", columnNames = {"workspace_id", "project_key"}),
		@UniqueConstraint(name = "uk_projects_id_workspace", columnNames = {"id", "workspace_id"})
})
public class Project {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 2000)
	private String description;

	@Column(name = "project_key", nullable = false, length = 50)
	private String key;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectStatus status;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "archived_at")
	private LocalDateTime archivedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now(Clock.systemUTC());
	}

	public UUID getId() { return id; }
	public Workspace getWorkspace() { return workspace; }
	public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getKey() { return key; }
	public void setKey(String key) { this.key = key; }
	public ProjectStatus getStatus() { return status; }
	public void setStatus(ProjectStatus status) { this.status = status; }
	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
	public LocalDate getDueDate() { return dueDate; }
	public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
	public User getCreatedBy() { return createdBy; }
	public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public LocalDateTime getArchivedAt() { return archivedAt; }
	public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}

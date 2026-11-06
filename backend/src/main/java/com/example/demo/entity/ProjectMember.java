package com.example.demo.entity;

import com.example.demo.enums.ProjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(
		name = "uk_project_members_project_user",
		columnNames = {"project_id", "user_id"}))
public class ProjectMember {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumns({
			@JoinColumn(name = "project_id", referencedColumnName = "id", nullable = false),
			@JoinColumn(name = "workspace_id", referencedColumnName = "workspace_id", nullable = false)
	})
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@PrePersist
	void onCreate() {
		joinedAt = LocalDateTime.now(Clock.systemUTC());
	}

	public UUID getId() { return id; }
	public Project getProject() { return project; }
	public void setProject(Project project) { this.project = project; }
	public User getUser() { return user; }
	public void setUser(User user) { this.user = user; }
	public ProjectRole getRole() { return role; }
	public void setRole(ProjectRole role) { this.role = role; }
	public LocalDateTime getJoinedAt() { return joinedAt; }
}

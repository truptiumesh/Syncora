package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "direct_conversations", uniqueConstraints = @UniqueConstraint(
		name = "uk_direct_conversations_pair",
		columnNames = {"workspace_id", "participant_one_id", "participant_two_id"}))
public class DirectConversation {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_one_id", nullable = false)
	private User participantOne;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_two_id", nullable = false)
	private User participantTwo;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

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
	public User getParticipantOne() { return participantOne; }
	public void setParticipantOne(User participantOne) { this.participantOne = participantOne; }
	public User getParticipantTwo() { return participantTwo; }
	public void setParticipantTwo(User participantTwo) { this.participantTwo = participantTwo; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void touch() { updatedAt = LocalDateTime.now(Clock.systemUTC()); }

	public User otherParticipant(UUID userId) {
		if (participantOne.getId().equals(userId)) return participantTwo;
		if (participantTwo.getId().equals(userId)) return participantOne;
		throw new IllegalArgumentException("The user is not a conversation participant.");
	}
}

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
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_attachments")
public class FileAttachment {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "storage_key", nullable = false, unique = true, length = 64)
	private String storageKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private LocalDateTime uploadedAt;

	@PrePersist
	void onCreate() {
		uploadedAt = LocalDateTime.now(Clock.systemUTC());
	}

	public UUID getId() { return id; }
	public String getOriginalFilename() { return originalFilename; }
	public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
	public String getStorageKey() { return storageKey; }
	public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
	public String getContentType() { return contentType; }
	public void setContentType(String contentType) { this.contentType = contentType; }
	public long getFileSize() { return fileSize; }
	public void setFileSize(long fileSize) { this.fileSize = fileSize; }
	public User getUploader() { return uploader; }
	public void setUploader(User uploader) { this.uploader = uploader; }
	public Workspace getWorkspace() { return workspace; }
	public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
	public Project getProject() { return project; }
	public void setProject(Project project) { this.project = project; }
	public LocalDateTime getUploadedAt() { return uploadedAt; }
}

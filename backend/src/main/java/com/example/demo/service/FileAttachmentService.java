package com.example.demo.service;

import com.example.demo.dto.response.FileAttachmentResponse;
import com.example.demo.entity.FileAttachment;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.exception.FileAttachmentNotFoundException;
import com.example.demo.exception.FileTooLargeException;
import com.example.demo.repository.FileAttachmentRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileAttachmentService {
	private static final Logger logger = LoggerFactory.getLogger(FileAttachmentService.class);
	private static final int MAX_PAGE_SIZE = 100;

	private final FileAttachmentRepository fileRepository;
	private final FileStorageService storageService;
	private final WorkspaceAuthorizationService workspaceAuthorizationService;
	private final ProjectAuthorizationService projectAuthorizationService;
	private final ApplicationEventPublisher eventPublisher;
	private final long maximumUploadBytes;

	public FileAttachmentService(
			FileAttachmentRepository fileRepository,
			FileStorageService storageService,
			WorkspaceAuthorizationService workspaceAuthorizationService,
			ProjectAuthorizationService projectAuthorizationService,
			ApplicationEventPublisher eventPublisher,
			@Value("${app.files.max-size}") DataSize maximumUploadSize) {
		this.fileRepository = fileRepository;
		this.storageService = storageService;
		this.workspaceAuthorizationService = workspaceAuthorizationService;
		this.projectAuthorizationService = projectAuthorizationService;
		this.eventPublisher = eventPublisher;
		this.maximumUploadBytes = maximumUploadSize.toBytes();
	}

	@Transactional
	public FileAttachmentResponse uploadToWorkspace(
			UUID workspaceId, String principalName, MultipartFile multipartFile) {
		WorkspaceMember member = workspaceAuthorizationService.requireMember(workspaceId, principalName);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		return upload(member.getWorkspace(), null, actor, multipartFile);
	}

	@Transactional
	public FileAttachmentResponse uploadToProject(
			UUID projectId, String principalName, MultipartFile multipartFile) {
		ProjectAuthorizationService.Access access =
				projectAuthorizationService.requireProjectAccess(projectId, principalName);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		return upload(access.project().getWorkspace(), access.project(), actor, multipartFile);
	}

	@Transactional(readOnly = true)
	public Page<FileAttachmentResponse> listWorkspace(
			UUID workspaceId, String principalName, int page, int size) {
		workspaceAuthorizationService.requireMember(workspaceId, principalName);
		PageRequest pageable = pageable(page, size);
		return fileRepository
				.findByWorkspace_IdAndProjectIsNullOrderByUploadedAtDescIdDesc(workspaceId, pageable)
				.map(FileAttachmentResponse::from);
	}

	@Transactional(readOnly = true)
	public Page<FileAttachmentResponse> listProject(
			UUID projectId, String principalName, int page, int size) {
		projectAuthorizationService.requireProjectAccess(projectId, principalName);
		return fileRepository.findByProject_IdOrderByUploadedAtDescIdDesc(
						projectId, pageable(page, size))
				.map(FileAttachmentResponse::from);
	}

	@Transactional(readOnly = true)
	public FileDownload download(UUID fileId, String principalName) {
		FileAttachment file = findFile(fileId);
		authorizeAccess(file, principalName);
		InputStreamResource content = storageService.open(file.getStorageKey());
		return new FileDownload(content, file.getOriginalFilename(), file.getContentType(), file.getFileSize());
	}

	@Transactional
	public void delete(UUID fileId, String principalName) {
		FileAttachment file = findFile(fileId);
		User actor = workspaceAuthorizationService.findActiveUser(principalName);
		authorizeDelete(file, actor, principalName);
		fileRepository.delete(file);
		publishActivity(
				actor,
				file,
				ActivityType.FILE_DELETED,
				"deleted file " + file.getOriginalFilename() + ".");
		scheduleStorageDeletion(List.of(file.getStorageKey()));
	}

	@Transactional
	public void scheduleCleanupForWorkspace(UUID workspaceId) {
		scheduleStorageDeletion(fileRepository.findStorageKeysByWorkspaceId(workspaceId));
	}

	@Transactional
	public void scheduleCleanupForProject(UUID projectId) {
		scheduleStorageDeletion(fileRepository.findStorageKeysByProjectId(projectId));
	}

	private FileAttachmentResponse upload(
			Workspace workspace, Project project, User actor, MultipartFile multipartFile) {
		if (multipartFile == null || multipartFile.isEmpty()) {
			throw new IllegalArgumentException("Select a non-empty file to upload.");
		}
		if (multipartFile.getSize() > maximumUploadBytes) {
			throw new FileTooLargeException();
		}
		String originalFilename = safeFilename(multipartFile.getOriginalFilename());
		FileStorageService.StoredFile stored = storageService.store(multipartFile);
		registerRollbackCleanup(stored.storageKey());

		FileAttachment file = new FileAttachment();
		file.setOriginalFilename(originalFilename);
		file.setStorageKey(stored.storageKey());
		file.setContentType(stored.contentType());
		file.setFileSize(multipartFile.getSize());
		file.setUploader(actor);
		file.setWorkspace(workspace);
		file.setProject(project);
		file = fileRepository.saveAndFlush(file);
		publishActivity(
				actor,
				file,
				ActivityType.FILE_UPLOADED,
				"uploaded file " + originalFilename + ".");
		return FileAttachmentResponse.from(file);
	}

	private void authorizeAccess(FileAttachment file, String principalName) {
		if (file.getProject() == null) {
			workspaceAuthorizationService.requireMember(file.getWorkspace().getId(), principalName);
		} else {
			projectAuthorizationService.requireProjectAccess(file.getProject().getId(), principalName);
		}
	}

	private void authorizeDelete(FileAttachment file, User actor, String principalName) {
		authorizeAccess(file, principalName);
		boolean uploader = file.getUploader().getId().equals(actor.getId());
		if (file.getProject() == null) {
			WorkspaceMember membership = workspaceAuthorizationService.requireMember(
					file.getWorkspace().getId(), principalName);
			boolean workspaceAdmin = membership.getRole() == WorkspaceRole.OWNER
					|| membership.getRole() == WorkspaceRole.ADMIN;
			if (!uploader && !workspaceAdmin) {
				throw new AccessDeniedException("Only the uploader or a workspace administrator can delete this file.");
			}
			return;
		}

		ProjectAuthorizationService.Access access = projectAuthorizationService.requireProjectAccess(
				file.getProject().getId(), principalName);
		boolean workspaceAdmin = projectAuthorizationService.isWorkspaceAdmin(access.workspaceMember());
		boolean projectManager = access.projectMember() != null
				&& access.projectMember().getRole() == ProjectRole.PROJECT_MANAGER;
		if (!uploader && !workspaceAdmin && !projectManager) {
			throw new AccessDeniedException("Only the uploader or a project administrator can delete this file.");
		}
	}

	private FileAttachment findFile(UUID fileId) {
		return fileRepository.findWithOwnerById(fileId).orElseThrow(FileAttachmentNotFoundException::new);
	}

	private PageRequest pageable(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
		}
		return PageRequest.of(
				page, size, Sort.by(Sort.Direction.DESC, "uploadedAt").and(Sort.by(Sort.Direction.DESC, "id")));
	}

	private String safeFilename(String suppliedFilename) {
		if (suppliedFilename == null || suppliedFilename.isBlank()) {
			throw new IllegalArgumentException("The uploaded file must have a filename.");
		}
		String normalizedSeparators = suppliedFilename.replace('\\', '/');
		String basename = normalizedSeparators.substring(normalizedSeparators.lastIndexOf('/') + 1)
				.replaceAll("[\\p{Cc}\\p{Cf}]", "")
				.trim();
		if (basename.isBlank() || basename.equals(".") || basename.equals("..")) {
			throw new IllegalArgumentException("The uploaded filename is invalid.");
		}
		return basename.length() <= 255 ? basename : basename.substring(0, 255);
	}

	private void publishActivity(
			User actor, FileAttachment file, ActivityType type, String description) {
		eventPublisher.publishEvent(ActivityEvent.create(
				actor,
				type,
				actor.getFullName() + " " + description,
				file.getWorkspace().getId(),
				file.getProject() == null ? null : file.getProject().getId(),
				null,
				null,
				null,
				List.of()));
	}

	private void registerRollbackCleanup(String storageKey) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					try {
						storageService.delete(storageKey);
					} catch (RuntimeException exception) {
						logger.error("Could not clean up uncommitted uploaded file {}", storageKey, exception);
					}
				}
			}
		});
	}

	private void scheduleStorageDeletion(List<String> storageKeys) {
		if (storageKeys.isEmpty()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (String storageKey : storageKeys) {
					try {
						storageService.delete(storageKey);
					} catch (RuntimeException exception) {
						logger.error("Could not remove deleted file from local storage {}", storageKey, exception);
					}
				}
			}
		});
	}

	public record FileDownload(InputStreamResource content, String filename, String contentType, long size) {}
}

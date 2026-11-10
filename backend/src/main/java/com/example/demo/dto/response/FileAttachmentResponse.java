package com.example.demo.dto.response;

import com.example.demo.entity.FileAttachment;
import java.time.LocalDateTime;
import java.util.UUID;

public record FileAttachmentResponse(
		UUID id,
		String filename,
		String contentType,
		long size,
		UUID uploaderId,
		String uploaderName,
		UUID workspaceId,
		UUID projectId,
		LocalDateTime uploadedAt) {

	public static FileAttachmentResponse from(FileAttachment file) {
		return new FileAttachmentResponse(
				file.getId(),
				file.getOriginalFilename(),
				file.getContentType(),
				file.getFileSize(),
				file.getUploader().getId(),
				file.getUploader().getFullName(),
				file.getWorkspace().getId(),
				file.getProject() == null ? null : file.getProject().getId(),
				file.getUploadedAt());
	}
}

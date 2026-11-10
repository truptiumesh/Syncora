package com.example.demo.controller;

import com.example.demo.dto.response.FileAttachmentResponse;
import com.example.demo.service.FileAttachmentService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class FileAttachmentController {
	private final FileAttachmentService fileAttachmentService;

	public FileAttachmentController(FileAttachmentService fileAttachmentService) {
		this.fileAttachmentService = fileAttachmentService;
	}

	@PostMapping(path = "/workspaces/{workspaceId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FileAttachmentResponse> uploadWorkspaceFile(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(201).body(fileAttachmentService.uploadToWorkspace(
				workspaceId, authentication.getName(), file));
	}

	@GetMapping("/workspaces/{workspaceId}/files")
	public Page<FileAttachmentResponse> listWorkspaceFiles(
			Authentication authentication,
			@PathVariable UUID workspaceId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return fileAttachmentService.listWorkspace(
				workspaceId, authentication.getName(), page, size);
	}

	@PostMapping(path = "/projects/{projectId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FileAttachmentResponse> uploadProjectFile(
			Authentication authentication,
			@PathVariable UUID projectId,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(201).body(fileAttachmentService.uploadToProject(
				projectId, authentication.getName(), file));
	}

	@GetMapping("/projects/{projectId}/files")
	public Page<FileAttachmentResponse> listProjectFiles(
			Authentication authentication,
			@PathVariable UUID projectId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return fileAttachmentService.listProject(
				projectId, authentication.getName(), page, size);
	}

	@GetMapping("/files/{fileId}/download")
	public ResponseEntity<InputStreamResource> download(
			Authentication authentication, @PathVariable UUID fileId) {
		FileAttachmentService.FileDownload download =
				fileAttachmentService.download(fileId, authentication.getName());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(download.contentType()))
				.contentLength(download.size())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(download.filename(), StandardCharsets.UTF_8)
						.build()
						.toString())
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.noStore())
				.body(download.content());
	}

	@DeleteMapping("/files/{fileId}")
	public ResponseEntity<Void> delete(
			Authentication authentication, @PathVariable UUID fileId) {
		fileAttachmentService.delete(fileId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}

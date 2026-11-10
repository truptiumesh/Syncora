package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.FileAttachment;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.FileAttachmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileAttachmentIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	private static final Path STORAGE_DIRECTORY = Path.of(
			System.getProperty("java.io.tmpdir"), "syncora-file-tests-" + UUID.randomUUID());
	private static final byte[] PDF_BYTES = "%PDF-1.7\nSYNCORA TEST\n".getBytes();

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private FileAttachmentRepository fileRepository;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private final List<User> testUsers = new ArrayList<>();
	private final List<UUID> testWorkspaces = new ArrayList<>();

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
		registry.add("app.files.storage-dir", () -> STORAGE_DIRECTORY.toString());
		registry.add("app.files.max-size", () -> "10MB");
		registry.add("spring.servlet.multipart.max-file-size", () -> "10MB");
		registry.add("spring.servlet.multipart.max-request-size", () -> "11MB");
	}

	@AfterEach
	void cleanUpTestFixtures() throws Exception {
		for (UUID workspaceId : testWorkspaces) {
			User owner = testUsers.stream()
					.filter(user -> jdbcTemplate.queryForObject(
							"SELECT EXISTS (SELECT 1 FROM workspaces WHERE id = ? AND owner_id = ?)",
							Boolean.class,
							workspaceId,
							user.getId()))
					.findFirst()
					.orElse(null);
			if (owner != null) {
				mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
						.andExpect(status().isNoContent());
			}
		}
		for (User user : testUsers) {
			if (userRepository.existsById(user.getId())) userRepository.deleteById(user.getId());
		}
		testWorkspaces.clear();
		testUsers.clear();
	}

	@Test
	void flywayV7CreatesFileMetadataAndIndexes() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.file_attachments') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.idx_file_attachments_workspace_uploaded') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.idx_file_attachments_project_uploaded') IS NOT NULL", Boolean.class));
	}

	@Test
	void workspaceFileUploadListsDownloadsDeletesAndPublishesActivity() throws Exception {
		User owner = createUser();
		UUID workspaceId = createWorkspace(owner);
		MockMultipartFile upload = pdf("../project-plan.pdf");

		MvcResult uploaded = mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(upload)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.filename", is("project-plan.pdf")))
				.andExpect(jsonPath("$.contentType", is("application/pdf")))
				.andExpect(jsonPath("$.size", is(PDF_BYTES.length)))
				.andExpect(jsonPath("$.uploaderId", is(owner.getId().toString())))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andReturn();
		UUID fileId = responseId(uploaded, "$.id");
		FileAttachment saved = fileRepository.findWithOwnerById(fileId).orElseThrow();
		assertTrue(Files.isRegularFile(storagePath(saved.getStorageKey())));

		mockMvc.perform(get("/api/v1/workspaces/{id}/files", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.param("page", "0")
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(1)))
				.andExpect(jsonPath("$.content[0].id", is(fileId.toString())));
		mockMvc.perform(get("/api/v1/files/{id}/download", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						org.hamcrest.Matchers.containsString("project-plan.pdf")))
				.andExpect(content().bytes(PDF_BYTES));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM activity_records WHERE workspace_id = ? AND activity_type = 'FILE_UPLOADED')",
				Boolean.class,
				workspaceId));

		mockMvc.perform(delete("/api/v1/files/{id}", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNoContent());
		assertFalse(fileRepository.existsById(fileId));
		assertFalse(Files.exists(storagePath(saved.getStorageKey())));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM activity_records WHERE workspace_id = ? AND activity_type = 'FILE_DELETED')",
				Boolean.class,
				workspaceId));
	}

	@Test
	void workspaceMembershipIsRequiredForFileOperations() throws Exception {
		User owner = createUser();
		User outsider = createUser();
		UUID workspaceId = createWorkspace(owner);
		UUID fileId = uploadWorkspaceFile(owner, workspaceId);

		mockMvc.perform(get("/api/v1/workspaces/{id}/files", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(pdf("private.pdf"))
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/files/{id}/download", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/files/{id}", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
	}

	@Test
	void projectFileAccessRequiresProjectMembershipAndHonorsManagerDeletion() throws Exception {
		User manager = createUser();
		User member = createUser();
		User workspaceOnlyMember = createUser();
		UUID workspaceId = createWorkspace(manager);
		addWorkspaceMember(manager, workspaceId, member);
		addWorkspaceMember(manager, workspaceId, workspaceOnlyMember);
		UUID projectId = createProject(manager, workspaceId);
		addProjectMember(manager, projectId, member);
		UUID fileId = uploadProjectFile(member, projectId);

		mockMvc.perform(get("/api/v1/projects/{id}/files", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(workspaceOnlyMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/v1/projects/{id}/files", projectId)
						.file(pdf("private.pdf"))
						.header(HttpHeaders.AUTHORIZATION, bearer(workspaceOnlyMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/files/{id}/download", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(workspaceOnlyMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/files/{id}", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(manager)))
				.andExpect(status().isNoContent());
	}

	@Test
	void rejectsUnsupportedAndOversizedUploadsAndHandlesMissingStoredFile() throws Exception {
		User owner = createUser();
		UUID workspaceId = createWorkspace(owner);
		MockMultipartFile unsupported = new MockMultipartFile(
				"file", "bad.pdf", "application/pdf", "not a pdf".getBytes());
		mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(unsupported)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isUnsupportedMediaType());

		byte[] oversizedBytes = new byte[10 * 1024 * 1024 + 1];
		System.arraycopy(PDF_BYTES, 0, oversizedBytes, 0, PDF_BYTES.length);
		MockMultipartFile oversized = new MockMultipartFile(
				"file", "large.pdf", "application/pdf", oversizedBytes);
		mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(oversized)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isPayloadTooLarge());

		UUID fileId = uploadWorkspaceFile(owner, workspaceId);
		FileAttachment saved = fileRepository.findWithOwnerById(fileId).orElseThrow();
		Files.delete(storagePath(saved.getStorageKey()));
		mockMvc.perform(get("/api/v1/files/{id}/download", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/files/{id}", fileId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNoContent());
	}

	@Test
	void unauthenticatedUsersCannotListOrUploadFiles() throws Exception {
		User owner = createUser();
		UUID workspaceId = createWorkspace(owner);
		mockMvc.perform(get("/api/v1/workspaces/{id}/files", workspaceId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(pdf("unauthorized.pdf")))
				.andExpect(status().isUnauthorized());
	}

	private UUID uploadWorkspaceFile(User user, UUID workspaceId) throws Exception {
		return responseId(mockMvc.perform(multipart("/api/v1/workspaces/{id}/files", workspaceId)
						.file(pdf("workspace.pdf"))
						.header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isCreated())
				.andReturn(), "$.id");
	}

	private UUID uploadProjectFile(User user, UUID projectId) throws Exception {
		return responseId(mockMvc.perform(multipart("/api/v1/projects/{id}/files", projectId)
						.file(pdf("project.pdf"))
						.header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isCreated())
				.andReturn(), "$.id");
	}

	private UUID createWorkspace(User owner) throws Exception {
		MvcResult response = mockMvc.perform(post("/api/v1/workspaces")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Files " + UUID.randomUUID() + "\",\"description\":\"File tests\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		UUID workspaceId = responseId(response, "$.id");
		testWorkspaces.add(workspaceId);
		return workspaceId;
	}

	private UUID createProject(User owner, UUID workspaceId) throws Exception {
		MvcResult response = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Attachment project " + UUID.randomUUID()
								+ "\",\"description\":\"File tests\",\"status\":\"ACTIVE\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(response, "$.id");
	}

	private void addWorkspaceMember(User owner, UUID workspaceId, User member) throws Exception {
		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private void addProjectMember(User manager, UUID projectId, User member) throws Exception {
		mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(manager))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private User createUser() {
		User user = new User();
		user.setFullName("File Test " + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail(UUID.randomUUID() + "@files.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		testUsers.add(userRepository.saveAndFlush(user));
		return user;
	}

	private MockMultipartFile pdf(String filename) {
		return new MockMultipartFile("file", filename, "application/pdf", PDF_BYTES);
	}

	private Path storagePath(String storageKey) {
		return STORAGE_DIRECTORY.resolve(UUID.fromString(storageKey) + ".blob");
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}
}

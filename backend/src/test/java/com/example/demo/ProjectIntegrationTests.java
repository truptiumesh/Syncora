package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.enums.ProjectRole;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WorkspaceMemberRepository workspaceMemberRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectMemberRepository projectMemberRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void flywayV3CreatesProjectTablesAndConstraints() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.projects') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.project_members') IS NOT NULL", Boolean.class));
	}

	@Test
	void creatorBecomesProjectManagerAndKeysAreUniquePerWorkspace() throws Exception {
		User owner = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Projects");
		CreatedProject first = createProject(owner, workspaceId, "Syncora Development", "PLANNING");
		CreatedProject collision = createProject(owner, workspaceId, "Syncora Development", "ACTIVE");
		UUID otherWorkspaceId = createWorkspace(owner, "Other projects");
		CreatedProject sameKeyElsewhere = createProject(owner, otherWorkspaceId, "Syncora Development", "PLANNING");

		assertEquals("SYNCORA-DEVELOPMENT", first.key());
		assertEquals("SYNCORA-DEVELOPMENT-2", collision.key());
		assertEquals(first.key(), sameKeyElsewhere.key());
		assertEquals(WorkspaceRole.OWNER, workspaceMemberRepository
				.findByWorkspace_IdAndUser_Id(workspaceId, owner.getId()).orElseThrow().getRole());
		assertEquals(1, projectMemberRepository.countByProject_IdAndRole(first.id(), ProjectRole.PROJECT_MANAGER));
		mockMvc.perform(get("/api/v1/projects/{id}", first.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole", is("PROJECT_MANAGER")))
				.andExpect(jsonPath("$.projectManager", is(owner.getFullName())));
	}

	@Test
	void projectCreationAndVisibilityRequireWorkspaceMembershipAndProjectAccess() throws Exception {
		User owner = createUser(Role.MEMBER);
		User workspaceMember = createUser(Role.MEMBER);
		User outsider = createUser(Role.ADMIN);
		UUID workspaceId = createWorkspace(owner, "Private projects");
		CreatedProject project = createProject(owner, workspaceId, "Only For Team", "ACTIVE");
		addWorkspaceMember(owner, workspaceId, workspaceMember);

		mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header("Authorization", bearer(outsider))
						.contentType(MediaType.APPLICATION_JSON)
						.content(projectJson("Denied", "PLANNING")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/workspaces/{id}/projects", workspaceId)
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/projects/{id}", project.id())
						.header("Authorization", bearer(workspaceMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/projects/{id}/members", project.id())
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
	}

	@Test
	void projectManagersCanAddWorkspaceMembersButCannotAddNonMembersOrDuplicateUsers() throws Exception {
		User owner = createUser(Role.MEMBER);
		User teammate = createUser(Role.MEMBER);
		User outsider = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Project team");
		CreatedProject project = createProject(owner, workspaceId, "App", "ACTIVE");
		addWorkspaceMember(owner, workspaceId, teammate);

		mockMvc.perform(post("/api/v1/projects/{id}/members", project.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(outsider.getEmail())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/projects/{id}/members", project.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(teammate.getEmail())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role", is("MEMBER")));
		mockMvc.perform(post("/api/v1/projects/{id}/members", project.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(teammate.getEmail())))
				.andExpect(status().isConflict());
		assertEquals(2, projectMemberRepository.countByProject_Id(project.id()));
	}

	@Test
	void projectMemberCannotManageProjectAndManagerCanUpdateArchiveAndRestore() throws Exception {
		User owner = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Lifecycle");
		CreatedProject project = createProject(owner, workspaceId, "Release", "PLANNING");
		addWorkspaceMember(owner, workspaceId, member);
		UUID memberId = addProjectMember(owner, project.id(), member);

		mockMvc.perform(put("/api/v1/projects/{id}", project.id())
						.header("Authorization", bearer(member))
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateProjectJson("Unauthorized", "ACTIVE")))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/projects/{id}", project.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateProjectJson("Release v2", "ACTIVE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Release v2")))
				.andExpect(jsonPath("$.status", is("ACTIVE")));
		mockMvc.perform(put("/api/v1/projects/{id}/archive", project.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ARCHIVED")));
		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?archived=true", workspaceId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)));
		mockMvc.perform(put("/api/v1/projects/{id}/restore", project.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ACTIVE")));
		assertTrue(projectRepository.existsById(project.id()));
		assertTrue(projectMemberRepository.findByIdAndProject_Id(memberId, project.id()).isPresent());
	}

	@Test
	void projectManagerCanPromoteButCannotRemoveOrDemoteTheLastManager() throws Exception {
		User owner = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Managers");
		CreatedProject project = createProject(owner, workspaceId, "Managers project", "PLANNING");
		addWorkspaceMember(owner, workspaceId, member);
		UUID memberId = addProjectMember(owner, project.id(), member);
		changeProjectRole(owner, project.id(), memberId, "PROJECT_MANAGER")
				.andExpect(status().isOk());

		UUID ownerMembership = projectMemberRepository.findByProject_IdAndUser_Id(project.id(), owner.getId())
				.orElseThrow().getId();
		changeProjectRole(owner, project.id(), ownerMembership, "MEMBER")
				.andExpect(status().isOk());
		changeProjectRole(member, project.id(), memberId, "MEMBER")
				.andExpect(status().isConflict());
		mockMvc.perform(delete("/api/v1/projects/{projectId}/members/{memberId}", project.id(), memberId)
						.header("Authorization", bearer(member)))
				.andExpect(status().isConflict());
		assertEquals(1, projectMemberRepository.countByProject_IdAndRole(project.id(), ProjectRole.PROJECT_MANAGER));
	}

	@Test
	void listingAppliesSearchStatusPaginationAndWorkspaceAdminVisibility() throws Exception {
		User owner = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Filters");
		CreatedProject alpha = createProject(owner, workspaceId, "Alpha Platform", "ACTIVE");
		createProject(owner, workspaceId, "Beta Platform", "PLANNING");
		addWorkspaceMember(owner, workspaceId, member);
		addProjectMember(owner, alpha.id(), member);

		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?search=ALPHA&status=ACTIVE&page=0&size=1", workspaceId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].name", is("Alpha Platform")));
		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?page=0&size=1", workspaceId)
						.header("Authorization", bearer(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].name", is("Alpha Platform")));
		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?page=0&size=1", workspaceId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content.length()", is(1)));
		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?size=101", workspaceId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void invalidProjectStatusFilterReturnsBadRequest() throws Exception {
		User owner = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Invalid filter");

		mockMvc.perform(get("/api/v1/workspaces/{id}/projects?status=NOT_A_STATUS", workspaceId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("A request parameter or path value is invalid.")));
		mockMvc.perform(get("/api/v1/projects/not-a-uuid")
						.header("Authorization", bearer(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void onlyWorkspaceAdministratorsCanDeleteProjects() throws Exception {
		User owner = createUser(Role.MEMBER);
		User projectManager = createUser(Role.MEMBER);
		UUID workspaceId = createWorkspace(owner, "Deletion");
		CreatedProject project = createProject(owner, workspaceId, "Deletion project", "PLANNING");
		addWorkspaceMember(owner, workspaceId, projectManager);
		UUID managerMembership = addProjectMember(owner, project.id(), projectManager);
		changeProjectRole(owner, project.id(), managerMembership, "PROJECT_MANAGER")
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/projects/{id}", project.id())
						.header("Authorization", bearer(projectManager)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/projects/{id}", project.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
		assertFalse(projectRepository.existsById(project.id()));
		assertEquals(0, projectMemberRepository.countByProject_Id(project.id()));
	}

	private User createUser(Role role) {
		User user = new User();
		user.setFullName("Project Test");
		user.setEmail(UUID.randomUUID() + "@project.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(role);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private UUID createWorkspace(User owner, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","description":"Project test workspace"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.id"));
	}

	private CreatedProject createProject(User actor, UUID workspaceId, String name, String status)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header("Authorization", bearer(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(projectJson(name, status)))
				.andExpect(status().isCreated())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new CreatedProject(
				UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id")),
				com.jayway.jsonpath.JsonPath.read(body, "$.key"));
	}

	private void addWorkspaceMember(User owner, UUID workspaceId, User user) throws Exception {
		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(user.getEmail())))
				.andExpect(status().isCreated());
	}

	private UUID addProjectMember(User manager, UUID projectId, User user) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
						.header("Authorization", bearer(manager))
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailJson(user.getEmail())))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.id"));
	}

	private org.springframework.test.web.servlet.ResultActions changeProjectRole(
			User actor, UUID projectId, UUID memberId, String role) throws Exception {
		return mockMvc.perform(put("/api/v1/projects/{projectId}/members/{memberId}/role", projectId, memberId)
				.header("Authorization", bearer(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"role":"%s"}
						""".formatted(role)));
	}

	private String projectJson(String name, String status) {
		return """
				{"name":"%s","description":"Project test","status":"%s","startDate":"2026-11-10","dueDate":"2027-01-10"}
				""".formatted(name, status);
	}

	private String updateProjectJson(String name, String status) {
		return projectJson(name, status);
	}

	private String emailJson(String email) {
		return """
				{"email":"%s"}
				""".formatted(email);
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private record CreatedProject(UUID id, String key) {
	}
}

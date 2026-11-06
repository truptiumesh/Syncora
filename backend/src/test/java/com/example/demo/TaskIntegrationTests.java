package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Task;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.TaskCommentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
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
class TaskIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private TaskCommentRepository taskCommentRepository;

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
	void flywayV4CreatesTaskTablesAndIndexes() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.tasks') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.task_comments') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.idx_tasks_project_priority') IS NOT NULL", Boolean.class));
	}

	@Test
	void projectMemberCanCreateTaskWithServerGeneratedKeyAndCreator() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Alpha Project");

		MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(taskJson("First task", "MEDIUM", null, "2027-01-10")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.taskKey", is("ALPHA-PROJECT-1")))
				.andExpect(jsonPath("$.status", is("TODO")))
				.andExpect(jsonPath("$.priority", is("MEDIUM")))
				.andExpect(jsonPath("$.createdBy", is(owner.getId().toString())))
				.andReturn();
		UUID taskId = responseId(result, "$.id");
		assertTrue(taskRepository.findById(taskId).isPresent());
	}

	@Test
	void taskEndpointsRejectUnauthenticatedAndNonWorkspaceUsers() throws Exception {
		User owner = createUser();
		User outsider = createUser();
		UUID projectId = createProject(owner, "Private Project");

		mockMvc.perform(get("/api/v1/projects/{id}/tasks", projectId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header("Authorization", bearer(outsider))
						.contentType(MediaType.APPLICATION_JSON)
						.content(taskJson("No access", "LOW", null, null)))
				.andExpect(status().isForbidden());
	}

	@Test
	void taskKeysAreUniqueWithinOneProject() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Same Project");

		CreatedTask first = createTask(owner, projectId, "First");
		CreatedTask second = createTask(owner, projectId, "Second");

		assertEquals("SAME-PROJECT-1", first.key());
		assertEquals("SAME-PROJECT-2", second.key());
		assertNotEquals(first.key(), second.key());
	}

	@Test
	void invalidTaskInputAndInvalidAssigneeAreRejected() throws Exception {
		User owner = createUser();
		User unrelated = createUser();
		UUID projectId = createProject(owner, "Validation Project");

		mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"  ","priority":"MEDIUM"}
								"""))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content(taskJson("Invalid assignee", "LOW", unrelated.getId().toString(), null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("The assignee must be an active member of this project.")));
	}

	@Test
	void existingProjectMemberCanBeAssignedAndUnassigned() throws Exception {
		User owner = createUser();
		User teammate = createUser();
		UUID projectId = createProject(owner, "Assignment Project");
		addWorkspaceMember(owner, projectId, teammate);
		addProjectMember(owner, projectId, teammate);

		CreatedTask task = createTask(owner, projectId, "Assign me");
		mockMvc.perform(patch("/api/v1/tasks/{id}/assignee", task.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"assigneeId":"%s"}
								""".formatted(teammate.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assigneeId", is(teammate.getId().toString())))
				.andExpect(jsonPath("$.assigneeName", is(teammate.getFullName())));
		mockMvc.perform(patch("/api/v1/tasks/{id}/assignee", task.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"assigneeId":null}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assigneeId").doesNotExist());
	}

	@Test
	void taskDetailsUpdatesStatusAndPriorityWorkAndSetCompletionTime() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Lifecycle Project");
		CreatedTask task = createTask(owner, projectId, "Original task");

		mockMvc.perform(get("/api/v1/tasks/{id}", task.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title", is("Original task")));
		mockMvc.perform(put("/api/v1/tasks/{id}", task.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"Updated task","description":"Updated description","dueDate":"2027-02-01"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title", is("Updated task")));
		mockMvc.perform(patch("/api/v1/tasks/{id}/status", task.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"DONE"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedAt").isNotEmpty());
		mockMvc.perform(patch("/api/v1/tasks/{id}/priority", task.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"priority":"URGENT"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priority", is("URGENT")));
	}

	@Test
	void taskSearchFiltersPaginationOverdueAndSafeSortingWork() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Filter Project");
		CreatedTask overdue = createTask(owner, projectId, "Urgent overdue work", "URGENT", "2020-01-01");
		createTask(owner, projectId, "Future low-priority work", "LOW", "2099-01-01");
		mockMvc.perform(patch("/api/v1/tasks/{id}/status", overdue.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status":"IN_PROGRESS"}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/projects/{id}/tasks?search=urgent&priority=URGENT&overdue=true&page=0&size=1&sortBy=dueDate&sortDirection=ASC", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].taskKey", is(overdue.key())));
		mockMvc.perform(get("/api/v1/projects/{id}/tasks?page=0&size=1", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andExpect(jsonPath("$.content.length()", is(1)));
		mockMvc.perform(get("/api/v1/projects/{id}/tasks?sortBy=notAField", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void commentsCanBeCreatedListedAndOnlyAuthorOrManagerCanModifyThem() throws Exception {
		User owner = createUser();
		User author = createUser();
		User otherMember = createUser();
		UUID projectId = createProject(owner, "Comment Project");
		addWorkspaceMember(owner, projectId, author);
		addProjectMember(owner, projectId, author);
		addWorkspaceMember(owner, projectId, otherMember);
		addProjectMember(owner, projectId, otherMember);
		CreatedTask task = createTask(owner, projectId, "Commented task");
		CreatedComment comment = createComment(author, task.id(), "Original comment");

		mockMvc.perform(get("/api/v1/tasks/{id}/comments", task.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(1)))
				.andExpect(jsonPath("$[0].authorId", is(author.getId().toString())));
		mockMvc.perform(put("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), comment.id())
						.header("Authorization", bearer(otherMember))
						.contentType(MediaType.APPLICATION_JSON)
						.content(commentJson("Unauthorized edit")))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), comment.id())
						.header("Authorization", bearer(otherMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), comment.id())
						.header("Authorization", bearer(author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(commentJson("Edited by author")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", is("Edited by author")));
		mockMvc.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", task.id(), comment.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
		assertEquals(0, taskCommentRepository.countByTask_Id(task.id()));
	}

	@Test
	void projectMembersCannotAccessTasksOrCommentsFromAnotherProject() throws Exception {
		User owner = createUser();
		User member = createUser();
		UUID firstProject = createProject(owner, "First project");
		UUID secondProject = createProject(owner, "Second project");
		addWorkspaceMember(owner, firstProject, member);
		addProjectMember(owner, firstProject, member);
		CreatedTask firstTask = createTask(owner, firstProject, "Visible");
		CreatedTask privateTask = createTask(owner, secondProject, "Private");

		mockMvc.perform(get("/api/v1/tasks/{id}", privateTask.id())
						.header("Authorization", bearer(member)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/tasks/{id}/comments", privateTask.id())
						.header("Authorization", bearer(member)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/projects/{id}/tasks", firstProject)
						.header("Authorization", bearer(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(1)))
				.andExpect(jsonPath("$.content[0].id", is(firstTask.id().toString())));
	}

	@Test
	void deletingTaskCascadesCommentsAndOnlyManagerCanDeleteTask() throws Exception {
		User owner = createUser();
		User member = createUser();
		UUID projectId = createProject(owner, "Delete project");
		addWorkspaceMember(owner, projectId, member);
		addProjectMember(owner, projectId, member);
		CreatedTask task = createTask(owner, projectId, "Delete with comments");
		createComment(member, task.id(), "Keep integrity");

		mockMvc.perform(delete("/api/v1/tasks/{id}", task.id())
						.header("Authorization", bearer(member)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/tasks/{id}", task.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
		assertFalse(taskRepository.existsById(task.id()));
		assertEquals(0, taskCommentRepository.countByTask_Id(task.id()));
	}

	private User createUser() {
		User user = new User();
		user.setFullName("Task Test " + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail(UUID.randomUUID() + "@task.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private UUID createProject(User owner, String name) throws Exception {
		MvcResult workspaceResponse = mockMvc.perform(post("/api/v1/workspaces")
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Task test workspace","description":"For task integration"}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		UUID workspaceId = responseId(workspaceResponse, "$.id");
		MvcResult projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","description":"Task test project","status":"ACTIVE"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(projectResponse, "$.id");
	}

	private CreatedTask createTask(User actor, UUID projectId, String title) throws Exception {
		return createTask(actor, projectId, title, "MEDIUM", "2099-01-01");
	}

	private CreatedTask createTask(
			User actor, UUID projectId, String title, String priority, String dueDate) throws Exception {
		String body = taskJson(title, priority, null, dueDate);
		MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header("Authorization", bearer(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		return new CreatedTask(
				responseId(result, "$.id"),
				com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.taskKey"));
	}

	private void addWorkspaceMember(User owner, UUID projectId, User user) throws Exception {
		UUID workspaceId = jdbcTemplate.queryForObject(
				"SELECT workspace_id FROM projects WHERE id = ?", UUID.class, projectId);
		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(user.getEmail())))
				.andExpect(status().isCreated());
	}

	private void addProjectMember(User manager, UUID projectId, User user) throws Exception {
		mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
						.header("Authorization", bearer(manager))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(user.getEmail())))
				.andExpect(status().isCreated());
	}

	private CreatedComment createComment(User author, UUID taskId, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
						.header("Authorization", bearer(author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(commentJson(content)))
				.andExpect(status().isCreated())
				.andReturn();
		return new CreatedComment(responseId(result, "$.id"));
	}

	private String taskJson(String title, String priority, String assigneeId, String dueDate) {
		return """
				{"title":"%s","description":"Task detail","priority":"%s","assigneeId":%s,"dueDate":%s}
				""".formatted(
				title,
				priority,
				assigneeId == null ? "null" : "\"" + assigneeId + "\"",
				dueDate == null ? "null" : "\"" + dueDate + "\"");
	}

	private String commentJson(String content) {
		return """
				{"content":"%s"}
				""".formatted(content);
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private record CreatedTask(UUID id, String key) {
	}

	private record CreatedComment(UUID id) {
	}
}

package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.enums.ActivityType;
import com.example.demo.enums.NotificationType;
import com.example.demo.enums.Role;
import com.example.demo.repository.ActivityRecordRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import com.example.demo.service.ActivityEvent;
import com.example.demo.service.ActivityEventPersistenceService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationActivityIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private ActivityRecordRepository activityRepository;
	@Autowired private ActivityEventPersistenceService eventPersistenceService;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private final List<User> testUsers = new ArrayList<>();
	private final List<UUID> testWorkspaces = new ArrayList<>();

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
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
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)));
			}
		}
		for (User user : testUsers) {
			if (userRepository.existsById(user.getId())) {
				userRepository.deleteById(user.getId());
			}
		}
		testWorkspaces.clear();
		testUsers.clear();
	}

	@Test
	void flywayV6CreatesNotificationAndActivityTablesAndIndexes() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '6' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.notifications') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.activity_records') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.idx_notifications_unread') IS NOT NULL", Boolean.class));
	}

	@Test
	void notificationOwnershipUnreadStateAndPaginationAreEnforced() throws Exception {
		User owner = createUser();
		User recipient = createUser();
		UUID workspaceId = createWorkspace(owner);
		addWorkspaceMember(owner, workspaceId, recipient);
		UUID projectId = createProject(owner, workspaceId, "Notification project");
		addProjectMember(owner, projectId, recipient);

		mockMvc.perform(get("/api/v1/notifications"))
				.andExpect(status().isUnauthorized());
		MvcResult list = mockMvc.perform(get("/api/v1/notifications")
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient))
						.param("page", "0")
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(1)))
				.andExpect(jsonPath("$.totalElements", is(2)))
				.andReturn();
		UUID notificationId = responseId(list, "$.content[0].id");
		mockMvc.perform(get("/api/v1/notifications")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", is(0)));
		mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/notifications/{id}", notificationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count", is(2)));

		mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read", is(true)));
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(jsonPath("$.count", is(1)));
		mockMvc.perform(patch("/api/v1/notifications/{id}/unread", notificationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read", is(false)));
		mockMvc.perform(patch("/api/v1/notifications/read-all")
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updatedCount", is(2)));
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(jsonPath("$.count", is(0)));
		mockMvc.perform(delete("/api/v1/notifications/{id}", notificationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isNoContent());
	}

	@Test
	void taskAssignmentsCommentsAndSelfActionsGenerateOnlyRelevantNotifications() throws Exception {
		User owner = createUser();
		User assignee = createUser();
		UUID workspaceId = createWorkspace(owner);
		addWorkspaceMember(owner, workspaceId, assignee);
		UUID projectId = createProject(owner, workspaceId, "Task notifications");
		addProjectMember(owner, projectId, assignee);
		notificationRepository.deleteAllByRecipient_Id(owner.getId());
		notificationRepository.deleteAllByRecipient_Id(assignee.getId());

		MvcResult assignedTask = mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Assigned work\",\"priority\":\"HIGH\",\"assigneeId\":\""
								+ assignee.getId() + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		UUID taskId = responseId(assignedTask, "$.id");
		assertEquals(1, notificationRepository.countByRecipient_IdAndReadIsFalse(assignee.getId()));
		assertEquals(0, notificationRepository.countByRecipient_IdAndReadIsFalse(owner.getId()));

		mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"A private comment preview is not copied.\"}"))
				.andExpect(status().isCreated());
		assertEquals(2, notificationRepository.countByRecipient_IdAndReadIsFalse(assignee.getId()));
		MvcResult notifications = mockMvc.perform(get("/api/v1/notifications")
						.header(HttpHeaders.AUTHORIZATION, bearer(assignee)))
				.andExpect(status().isOk())
				.andReturn();
		assertFalse(notifications.getResponse().getContentAsString()
				.contains("A private comment preview is not copied."));

		mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"Self assigned\",\"priority\":\"LOW\",\"assigneeId\":\""
								+ owner.getId() + "\"}"))
				.andExpect(status().isCreated());
		assertEquals(0, notificationRepository.countByRecipient_IdAndReadIsFalse(owner.getId()));

		assignee.setActive(false);
		userRepository.saveAndFlush(assignee);
		UUID duplicateEventWorkspaceId = workspaceId;
		ActivityEvent inactiveRecipientEvent = ActivityEvent.create(
				owner,
				ActivityType.TASK_ASSIGNED,
				owner.getFullName() + " assigned an inactive recipient.",
				duplicateEventWorkspaceId,
				projectId,
				taskId,
				null,
				null,
				List.of(new ActivityEvent.NotificationTarget(
						assignee.getId(),
						NotificationType.TASK_ASSIGNED,
						"Task assigned",
						"You were assigned a task.")));
		long inactiveUnreadBefore = notificationRepository.countByRecipient_IdAndReadIsFalse(assignee.getId());
		eventPersistenceService.persist(inactiveRecipientEvent);
		assertEquals(inactiveUnreadBefore, notificationRepository.countByRecipient_IdAndReadIsFalse(assignee.getId()));
	}

	@Test
	void activityFeedIsPaginatedAndScopedByWorkspaceAndProjectMembership() throws Exception {
		User manager = createUser();
		User workspaceOnlyMember = createUser();
		User outsider = createUser();
		UUID workspaceId = createWorkspace(manager);
		addWorkspaceMember(manager, workspaceId, workspaceOnlyMember);
		UUID projectId = createProject(manager, workspaceId, "Private activity");
		UUID otherWorkspaceId = createWorkspace(outsider);

		for (int index = 0; index < 3; index++) {
			mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
							.header(HttpHeaders.AUTHORIZATION, bearer(manager))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"title\":\"Activity task " + index + "\",\"priority\":\"MEDIUM\"}"))
					.andExpect(status().isCreated());
		}

		mockMvc.perform(get("/api/v1/workspaces/{id}/activity", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/projects/{id}/activity", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(workspaceOnlyMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/projects/{id}/activity", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(manager))
						.param("page", "0")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(2)))
				.andExpect(jsonPath("$.totalElements", is(4)));
		mockMvc.perform(get("/api/v1/activity")
						.header(HttpHeaders.AUTHORIZATION, bearer(manager))
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.workspaceId == '" + otherWorkspaceId + "')]").isEmpty());
		mockMvc.perform(get("/api/v1/workspaces/{id}/activity", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(workspaceOnlyMember)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.projectId != null)]").isEmpty());
	}

	@Test
	void duplicateApplicationEventIsPersistedOnlyOnce() throws Exception {
		User owner = createUser();
		UUID workspaceId = createWorkspace(owner);
		long countBefore = activityRepository.countByWorkspace_Id(workspaceId);
		ActivityEvent event = ActivityEvent.create(
				owner,
				ActivityType.WORKSPACE_UPDATED,
				owner.getFullName() + " updated the workspace.",
				workspaceId,
				null,
				null,
				null,
				null,
				List.of());
		eventPersistenceService.persist(event);
		eventPersistenceService.persist(event);
		assertEquals(countBefore + 1, activityRepository.countByWorkspace_Id(workspaceId));
		assertTrue(activityRepository.existsByEventId(event.eventId()));
	}

	private UUID createWorkspace(User owner) throws Exception {
		MvcResult response = mockMvc.perform(post("/api/v1/workspaces")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Notifications " + UUID.randomUUID()
								+ "\",\"description\":\"Module 6 test workspace\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		UUID workspaceId = responseId(response, "$.id");
		testWorkspaces.add(workspaceId);
		return workspaceId;
	}

	private UUID createProject(User owner, UUID workspaceId, String name) throws Exception {
		MvcResult response = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + " " + UUID.randomUUID()
								+ "\",\"description\":\"Activity test\",\"status\":\"ACTIVE\"}"))
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

	private void addProjectMember(User owner, UUID projectId, User member) throws Exception {
		mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + member.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private User createUser() {
		User user = new User();
		user.setFullName("Activity Test " + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail(UUID.randomUUID() + "@activity.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		testUsers.add(userRepository.saveAndFlush(user));
		return user;
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}
}

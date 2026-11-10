package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dto.response.ChatMessageResponse;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ChatWebSocketAuthInterceptor;
import com.example.demo.security.JwtService;
import com.example.demo.service.ChatMessageChangedEvent;
import com.example.demo.service.ChatMessageBroadcastListener;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private ChatMessageRepository chatMessageRepository;
	@Autowired private ChatWebSocketAuthInterceptor webSocketAuthInterceptor;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void flywayV5CreatesChatTableAndChronologicalIndexes() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '5' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.chat_messages') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.idx_chat_messages_project_created') IS NOT NULL", Boolean.class));
	}

	@Test
	void projectMemberCanReadAndSendPersistedMessagesWithAuthenticatedSender() throws Exception {
		User owner = createUser();
		User member = createUser();
		UUID projectId = createProject(owner, "Chat project");
		addWorkspaceMember(owner, projectId, member);
		addProjectMember(owner, projectId, member);

		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(0)));
		String body = "{\"content\":\"Deployment is ready\",\"senderId\":\"" + owner.getId() + "\"}";
		MvcResult response = mockMvc.perform(post("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(member))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content", is("Deployment is ready")))
				.andExpect(jsonPath("$.senderId", is(member.getId().toString())))
				.andExpect(jsonPath("$.senderName", is(member.getFullName())))
				.andReturn();
		UUID messageId = responseId(response, "$.id");
		assertTrue(chatMessageRepository.findById(messageId).isPresent());
		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id", is(messageId.toString())));
	}

	@Test
	void chatHistoryRequiresAuthenticationAndProjectMembership() throws Exception {
		User owner = createUser();
		User outsider = createUser();
		UUID projectId = createProject(owner, "Private chat");
		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(outsider))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Unauthorized message\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void blankOversizedAndInvalidPageRequestsAreRejected() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Validation chat");
		mockMvc.perform(post("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + "x".repeat(4001) + "\"}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/projects/{id}/messages?page=0&size=101", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void authorsMayEditDeleteOwnMessagesButNotOtherUsersMessages() throws Exception {
		User owner = createUser();
		User teammate = createUser();
		UUID projectId = createProject(owner, "Own messages");
		addWorkspaceMember(owner, projectId, teammate);
		addProjectMember(owner, projectId, teammate);
		UUID ownerMessage = send(owner, projectId, "Owner says hi");
		UUID memberMessage = send(teammate, projectId, "Member says hi");

		mockMvc.perform(put("/api/v1/messages/{id}", ownerMessage)
						.header("Authorization", bearer(teammate))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Changed by someone else\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/messages/{id}", ownerMessage)
						.header("Authorization", bearer(teammate)))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/messages/{id}", memberMessage)
						.header("Authorization", bearer(teammate))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Edited by author\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.editedAt").isNotEmpty())
				.andExpect(jsonPath("$.content", is("Edited by author")));
		mockMvc.perform(delete("/api/v1/messages/{id}", memberMessage)
						.header("Authorization", bearer(teammate)))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[1].deleted", is(true)))
				.andExpect(jsonPath("$.content[1].content").doesNotExist());
	}

	@Test
	void projectManagerCanModerateMemberMessagesAndHistoryIsProjectScoped() throws Exception {
		User owner = createUser();
		User member = createUser();
		UUID projectId = createProject(owner, "Chat one");
		UUID otherProjectId = createProject(owner, "Chat two");
		addWorkspaceMember(owner, projectId, member);
		addProjectMember(owner, projectId, member);
		UUID messageId = send(member, projectId, "Member message");
		UUID otherMessageId = send(owner, otherProjectId, "Different project message");

		mockMvc.perform(delete("/api/v1/messages/{id}", messageId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(1)))
				.andExpect(jsonPath("$.content[0].id", is(messageId.toString())));
		assertTrue(chatMessageRepository.findById(otherMessageId).isPresent());
	}

	@Test
	void websocketRequiresJwtAndChecksProjectMembershipOnSubscriptionAndSend() throws Exception {
		User owner = createUser();
		User outsider = createUser();
		UUID projectId = createProject(owner, "Socket project");

		StompHeaderAccessor missingAuth = StompHeaderAccessor.create(StompCommand.CONNECT);
		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				stompMessage(missingAuth), mock(MessageChannel.class)));

		StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
		connect.addNativeHeader("Authorization", bearer(owner));
		Message<?> connected = webSocketAuthInterceptor.preSend(
				stompMessage(connect), mock(MessageChannel.class));
		assertNotNull(StompHeaderAccessor.wrap(connected).getUser());

		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				projectFrame(StompCommand.SUBSCRIBE, "/topic/projects/" + projectId + "/messages", outsider),
				mock(MessageChannel.class)));
		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				projectFrame(StompCommand.SEND, "/app/projects/" + projectId + "/messages", outsider),
				mock(MessageChannel.class)));
		Message<?> subscription = webSocketAuthInterceptor.preSend(
				projectFrame(StompCommand.SUBSCRIBE, "/topic/projects/" + projectId + "/messages", owner),
				mock(MessageChannel.class));
		assertNotNull(subscription);
		Message<?> notificationSubscription = webSocketAuthInterceptor.preSend(
				projectFrame(StompCommand.SUBSCRIBE, "/user/queue/notifications", owner),
				mock(MessageChannel.class));
		assertNotNull(notificationSubscription);
		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				projectFrame(
						StompCommand.SUBSCRIBE,
						"/user/other@example.com/queue/notifications",
						owner),
				mock(MessageChannel.class)));
		Message<?> send = webSocketAuthInterceptor.preSend(
				projectFrame(StompCommand.SEND, "/app/projects/" + projectId + "/messages", owner),
				mock(MessageChannel.class));
		assertNotNull(send);
	}

	@Test
	void committedMessageEventBroadcastsPersistedResponseToProjectDestination() throws Exception {
		User owner = createUser();
		UUID projectId = createProject(owner, "Broadcast");
		UUID messageId = send(owner, projectId, "Persisted");
		ChatMessage message = chatMessageRepository.findById(messageId).orElseThrow();
		ChatMessageResponse response = ChatMessageResponse.from(message);
		SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);

		new ChatMessageBroadcastListener(template)
				.broadcast(new ChatMessageChangedEvent(projectId, response));

		verify(template).convertAndSend("/topic/projects/" + projectId + "/messages", response);
	}

	private User createUser() {
		User user = new User();
		user.setFullName("Chat Test " + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail(UUID.randomUUID() + "@chat.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private UUID createProject(User owner, String name) throws Exception {
		MvcResult workspaceResponse = mockMvc.perform(post("/api/v1/workspaces")
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Chat workspace\",\"description\":\"Test chat\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		UUID workspaceId = responseId(workspaceResponse, "$.id");
		MvcResult projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"description\":\"Chat project\",\"status\":\"ACTIVE\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(projectResponse, "$.id");
	}

	private void addWorkspaceMember(User owner, UUID projectId, User user) throws Exception {
		UUID workspaceId = jdbcTemplate.queryForObject(
				"SELECT workspace_id FROM projects WHERE id = ?", UUID.class, projectId);
		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + user.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private void addProjectMember(User manager, UUID projectId, User user) throws Exception {
		mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
						.header("Authorization", bearer(manager))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + user.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private UUID send(User sender, UUID projectId, String content) throws Exception {
		MvcResult response = mockMvc.perform(post("/api/v1/projects/{id}/messages", projectId)
						.header("Authorization", bearer(sender))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(response, "$.id");
	}

	private Message<byte[]> projectFrame(StompCommand command, String destination, User user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		Principal principal = () -> user.getEmail();
		accessor.setUser(principal);
		return stompMessage(accessor);
	}

	private Message<byte[]> stompMessage(StompHeaderAccessor accessor) {
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}
}

package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.DirectConversationRepository;
import com.example.demo.repository.DirectMessageRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.ChatWebSocketAuthInterceptor;
import com.example.demo.security.JwtService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectMessageIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private DirectConversationRepository conversationRepository;
	@Autowired private DirectMessageRepository messageRepository;
	@Autowired private JwtService jwtService;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private ChatWebSocketAuthInterceptor webSocketAuthInterceptor;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void directMessagesPersistAndAreVisibleOnlyToTheirWorkspaceParticipants() throws Exception {
		User owner = createUser();
		User recipient = createUser();
		User otherMember = createUser();
		User outsider = createUser();
		UUID workspaceId = createWorkspace(owner);
		addWorkspaceMember(owner, workspaceId, recipient);
		addWorkspaceMember(owner, workspaceId, otherMember);

		MvcResult createdConversation = mockMvc.perform(post(
						"/api/v1/workspaces/{workspaceId}/direct-conversations", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipientId\":\"" + recipient.getId() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.recipientId", is(recipient.getId().toString())))
				.andReturn();
		UUID conversationId = responseId(createdConversation, "$.id");

		mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/direct-conversations", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipientId\":\"" + recipient.getId() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(conversationId.toString())));
		assertEquals(1, conversationRepository.findForMember(workspaceId, owner.getId()).size());

		mockMvc.perform(post("/api/v1/direct-conversations/{id}/messages", conversationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"A private update\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.senderId", is(owner.getId().toString())));
		assertEquals(1, messageRepository.countByConversation_Id(conversationId));

		mockMvc.perform(get("/api/v1/direct-conversations/{id}/messages", conversationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()", is(1)))
				.andExpect(jsonPath("$.content[0].content", is("A private update")));
		assertNotNull(webSocketAuthInterceptor.preSend(
				directFrame(StompCommand.SUBSCRIBE, "/topic/direct/" + conversationId, owner),
				org.mockito.Mockito.mock(MessageChannel.class)));
		assertNotNull(webSocketAuthInterceptor.preSend(
				directFrame(StompCommand.SEND, "/app/direct/" + conversationId + "/messages", recipient),
				org.mockito.Mockito.mock(MessageChannel.class)));
		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				directFrame(StompCommand.SUBSCRIBE, "/topic/direct/" + conversationId, otherMember),
				org.mockito.Mockito.mock(MessageChannel.class)));
		assertThrows(MessagingException.class, () -> webSocketAuthInterceptor.preSend(
				directFrame(StompCommand.SEND, "/app/direct/" + conversationId + "/messages", otherMember),
				org.mockito.Mockito.mock(MessageChannel.class)));
		mockMvc.perform(get("/api/v1/direct-conversations/{id}/messages", conversationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(otherMember)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/direct-conversations/{id}/messages", conversationId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/direct-conversations", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
				.andExpect(status().isForbidden());

		recipient.setActive(false);
		userRepository.saveAndFlush(recipient);
		mockMvc.perform(get("/api/v1/direct-conversations/{id}/messages", conversationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
				.andExpect(status().isForbidden());
	}

	@Test
	void flywayV8CreatesConversationAndMessageTablesWithMembershipConstraints() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success", Integer.class));
		assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.direct_conversations') IS NOT NULL", Boolean.class));
		assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.direct_messages') IS NOT NULL", Boolean.class));
	}

	private User createUser() {
		User user = new User();
		user.setFullName("Direct Message User");
		user.setEmail(UUID.randomUUID() + "@direct.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private UUID createWorkspace(User owner) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Private messages\",\"description\":\"Tests\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		return responseId(result, "$.id");
	}

	private void addWorkspaceMember(User owner, UUID workspaceId, User user) throws Exception {
		mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + user.getEmail() + "\"}"))
				.andExpect(status().isCreated());
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}

	private Message<byte[]> directFrame(StompCommand command, String destination, User user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		Principal principal = user::getEmail;
		accessor.setUser(principal);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}

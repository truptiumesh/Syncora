package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.dto.request.ChatMessageRequest;
import com.example.demo.dto.response.ChatMessageResponse;
import com.example.demo.dto.response.NotificationResponse;
import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.enums.NotificationType;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtService;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChatWebSocketIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@LocalServerPort
	private int port;

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository userRepository;
	@Autowired private ChatMessageRepository chatMessageRepository;
	@Autowired private NotificationRepository notificationRepository;
	@Autowired private JwtService jwtService;
	@Autowired private SimpUserRegistry simpUserRegistry;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void authorizedStompClientReceivesPersistedServerMessage() throws Exception {
		User owner = createUser();
		UUID workspaceId = null;
		StompSession session = null;
		WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setPoolSize(1);
		taskScheduler.setThreadNamePrefix("stomp-test-");
		taskScheduler.initialize();
		stompClient.setTaskScheduler(taskScheduler);
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		try {
			MvcResult workspaceResponse = mockMvc.perform(post("/api/v1/workspaces")
							.header(HttpHeaders.AUTHORIZATION, bearer(owner))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"Socket test workspace\",\"description\":\"Temporary test data\"}"))
					.andExpect(status().isCreated())
					.andReturn();
			workspaceId = responseId(workspaceResponse, "$.id");
			MvcResult projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
							.header(HttpHeaders.AUTHORIZATION, bearer(owner))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"Socket test project\",\"description\":\"Temporary test data\",\"status\":\"ACTIVE\"}"))
					.andExpect(status().isCreated())
					.andReturn();
			UUID projectId = responseId(projectResponse, "$.id");

			StompHeaders connectHeaders = new StompHeaders();
			connectHeaders.add(HttpHeaders.AUTHORIZATION, bearer(owner));
			WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
			handshakeHeaders.setOrigin("http://localhost:5173");
			String url = "ws://localhost:" + port + "/ws";
			session = stompClient.connectAsync(
					URI.create(url), handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
					.get(10, TimeUnit.SECONDS);

			CompletableFuture<ChatMessageResponse> received = new CompletableFuture<>();
			session.subscribe(
					"/topic/projects/" + projectId + "/messages", new StompFrameHandler() {
				@Override
				public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
					return ChatMessageResponse.class;
				}

				@Test
				void persistedNotificationIsDeliveredOnlyToItsIntendedStompUser() throws Exception {
					User owner = createUser();
					User recipient = createUser();
					UUID workspaceId = null;
					StompSession ownerSession = null;
					StompSession recipientSession = null;
					WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
					ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
					taskScheduler.setPoolSize(1);
					taskScheduler.setThreadNamePrefix("notification-stomp-test-");
					taskScheduler.initialize();
					stompClient.setTaskScheduler(taskScheduler);
					stompClient.setMessageConverter(new JacksonJsonMessageConverter());
					try {
						MvcResult workspaceResponse = mockMvc.perform(post("/api/v1/workspaces")
										.header(HttpHeaders.AUTHORIZATION, bearer(owner))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"name\":\"Notification socket workspace\",\"description\":\"Temporary test data\"}"))
								.andExpect(status().isCreated())
								.andReturn();
						workspaceId = responseId(workspaceResponse, "$.id");
						mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
										.header(HttpHeaders.AUTHORIZATION, bearer(owner))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"email\":\"" + recipient.getEmail() + "\"}"))
								.andExpect(status().isCreated());
						MvcResult projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
										.header(HttpHeaders.AUTHORIZATION, bearer(owner))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"name\":\"Notification socket project\",\"description\":\"Test project\",\"status\":\"ACTIVE\"}"))
								.andExpect(status().isCreated())
								.andReturn();
						UUID projectId = responseId(projectResponse, "$.id");
						mockMvc.perform(post("/api/v1/projects/{id}/members", projectId)
										.header(HttpHeaders.AUTHORIZATION, bearer(owner))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"email\":\"" + recipient.getEmail() + "\"}"))
								.andExpect(status().isCreated());
						notificationRepository.deleteAllByRecipient_Id(owner.getId());
						notificationRepository.deleteAllByRecipient_Id(recipient.getId());

						ownerSession = connect(stompClient, owner);
						recipientSession = connect(stompClient, recipient);
						CompletableFuture<NotificationResponse> ownerReceived = new CompletableFuture<>();
						CompletableFuture<NotificationResponse> recipientReceived = new CompletableFuture<>();
						ownerSession.subscribe("/user/queue/notifications", notificationFrame(ownerReceived));
						recipientSession.subscribe("/user/queue/notifications", notificationFrame(recipientReceived));
						awaitSubscription(owner.getEmail(), "/queue/notifications");
						awaitSubscription(recipient.getEmail(), "/queue/notifications");

						mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
										.header(HttpHeaders.AUTHORIZATION, bearer(owner))
										.contentType(MediaType.APPLICATION_JSON)
										.content("{\"title\":\"Real-time notification\",\"priority\":\"HIGH\",\"assigneeId\":\""
												+ recipient.getId() + "\"}"))
								.andExpect(status().isCreated());

						NotificationResponse notification = recipientReceived.get(10, TimeUnit.SECONDS);
						assertEquals(NotificationType.TASK_ASSIGNED, notification.type());
						assertEquals(owner.getId(), notification.actorId());
						assertEquals(1, notificationRepository.countByRecipient_IdAndReadIsFalse(recipient.getId()));
						assertEquals(0, notificationRepository.countByRecipient_IdAndReadIsFalse(owner.getId()));
						assertFalse(ownerReceived.isDone());
					} finally {
						if (ownerSession != null && ownerSession.isConnected()) {
							ownerSession.disconnect();
						}
						if (recipientSession != null && recipientSession.isConnected()) {
							recipientSession.disconnect();
						}
						stompClient.stop();
						taskScheduler.shutdown();
						if (workspaceId != null) {
							mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
									.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
									.andExpect(status().isNoContent());
						}
						userRepository.delete(owner);
						userRepository.delete(recipient);
					}
				}

				private StompSession connect(WebSocketStompClient stompClient, User user) throws Exception {
					StompHeaders connectHeaders = new StompHeaders();
					connectHeaders.add(HttpHeaders.AUTHORIZATION, bearer(user));
					WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
					handshakeHeaders.setOrigin("http://localhost:5173");
					return stompClient.connectAsync(
							URI.create("ws://localhost:" + port + "/ws"),
							handshakeHeaders,
							connectHeaders,
							new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
				}

				private StompFrameHandler notificationFrame(CompletableFuture<NotificationResponse> received) {
					return new StompFrameHandler() {
						@Override
						public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
							return NotificationResponse.class;
						}

						@Override
						public void handleFrame(StompHeaders headers, Object payload) {
							received.complete((NotificationResponse) payload);
						}
					};
				}

				@Override
				public void handleFrame(StompHeaders headers, Object payload) {
					received.complete((ChatMessageResponse) payload);
				}
			});
			awaitSubscription(owner.getEmail(), "/topic/projects/" + projectId + "/messages");
			session.send(
				"/app/projects/" + projectId + "/messages",
					new ChatMessageRequest("Persist before broadcast"));
			long persistenceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (chatMessageRepository.countByProject_Id(projectId) == 0
					&& System.nanoTime() < persistenceDeadline) {
				Thread.sleep(50);
			}
			assertEquals(1, chatMessageRepository.countByProject_Id(projectId));

			ChatMessageResponse message = received.get(10, TimeUnit.SECONDS);
			assertNotNull(message.id());
			assertEquals(projectId, message.projectId());
			assertEquals(owner.getId(), message.senderId());
			assertEquals("Persist before broadcast", message.content());
		} finally {
			if (session != null && session.isConnected()) {
				session.disconnect();
			}
			stompClient.stop();
			taskScheduler.shutdown();
			if (workspaceId != null) {
				mockMvc.perform(delete("/api/v1/workspaces/{id}", workspaceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
						.andExpect(status().isNoContent());
			}
			userRepository.delete(owner);
		}
	}

	private void awaitSubscription(String username, String destination) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			SimpUser user = simpUserRegistry.getUser(username);
			if (user != null && user.getSessions().stream()
					.flatMap(session -> session.getSubscriptions().stream())
					.anyMatch(subscription -> subscription.getDestination() != null
							&& (subscription.getDestination().equals(destination)
									|| subscription.getDestination().equals("/user" + destination)
									|| subscription.getDestination().startsWith(destination + "-user")))) {
				return;
			}
			Thread.sleep(25);
		}
		assertTrue(false, "The STOMP subscription was not registered.");
	}

	private User createUser() {
		User user = new User();
		user.setFullName("Socket Test " + UUID.randomUUID().toString().substring(0, 8));
		user.setEmail(UUID.randomUUID() + "@socket.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(Role.MEMBER);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private UUID responseId(MvcResult result, String jsonPath) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), jsonPath));
	}
}

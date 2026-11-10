package com.example.demo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.demo.dto.response.DirectMessageResponse;
import com.example.demo.service.DirectMessageBroadcastListener;
import com.example.demo.service.DirectMessageCreatedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class DirectMessageBroadcastListenerTests {
	@Test
	void broadcastsPersistedMessagesOnlyToTheConversationDestination() {
		SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
		DirectMessageBroadcastListener listener = new DirectMessageBroadcastListener(messagingTemplate);
		UUID conversationId = UUID.randomUUID();
		DirectMessageResponse response = new DirectMessageResponse(
				UUID.randomUUID(), conversationId, UUID.randomUUID(), "Alex", "Hello",
				LocalDateTime.of(2026, 1, 1, 10, 0));

		listener.broadcast(new DirectMessageCreatedEvent(conversationId, response));

		verify(messagingTemplate).convertAndSend("/topic/direct/" + conversationId, response);
	}
}

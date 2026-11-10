package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DirectMessageBroadcastListener {
	private static final Logger logger = LoggerFactory.getLogger(DirectMessageBroadcastListener.class);

	private final SimpMessagingTemplate messagingTemplate;

	public DirectMessageBroadcastListener(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void broadcast(DirectMessageCreatedEvent event) {
		try {
			messagingTemplate.convertAndSend(
					"/topic/direct/" + event.conversationId(), event.message());
		} catch (RuntimeException exception) {
			logger.error("Direct message {} was persisted but real-time delivery failed.",
					event.message().id(), exception);
		}
	}
}

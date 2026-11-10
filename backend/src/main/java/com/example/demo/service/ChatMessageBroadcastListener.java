package com.example.demo.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatMessageBroadcastListener {
	private final SimpMessagingTemplate messagingTemplate;

	public ChatMessageBroadcastListener(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void broadcast(ChatMessageChangedEvent event) {
		messagingTemplate.convertAndSend(
				"/topic/projects/" + event.projectId() + "/messages", event.message());
	}
}

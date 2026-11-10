package com.example.demo.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ActivityEventListener {
	private static final Logger logger = LoggerFactory.getLogger(ActivityEventListener.class);

	private final ActivityEventPersistenceService persistenceService;
	private final SimpMessagingTemplate messagingTemplate;

	public ActivityEventListener(
			ActivityEventPersistenceService persistenceService,
			SimpMessagingTemplate messagingTemplate) {
		this.persistenceService = persistenceService;
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(ActivityEvent event) {
		List<NotificationDelivery> persisted;
		try {
			persisted = persistenceService.persist(event);
		} catch (RuntimeException exception) {
			logger.error("Could not persist activity event {}", event.eventId(), exception);
			return;
		}
		for (NotificationDelivery delivery : persisted) {
			try {
				messagingTemplate.convertAndSendToUser(
						delivery.recipientName(), "/queue/notifications", delivery.notification());
			} catch (RuntimeException exception) {
				logger.error(
						"Could not deliver notification {} to user {}",
						delivery.notification().id(),
						delivery.recipientName(),
						exception);
			}
		}
	}

}

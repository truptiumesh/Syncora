package com.example.demo.service;

import com.example.demo.dto.response.DirectMessageResponse;
import java.util.UUID;

public record DirectMessageCreatedEvent(UUID conversationId, DirectMessageResponse message) {
}

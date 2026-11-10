package com.example.demo.service;

import com.example.demo.dto.response.ChatMessageResponse;
import java.util.UUID;

public record ChatMessageChangedEvent(UUID projectId, ChatMessageResponse message) {
}

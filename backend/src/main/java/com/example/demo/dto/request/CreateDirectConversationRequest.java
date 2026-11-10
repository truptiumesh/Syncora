package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDirectConversationRequest(@NotNull UUID recipientId) {
}

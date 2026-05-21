package com.aioj.next.contract.ai;

import java.time.Instant;

public record AiChatMessageResponse(String conversationId, String role, String content, String model, Instant createdAt) {
}


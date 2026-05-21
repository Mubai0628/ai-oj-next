package com.aioj.next.contract.ai;

import java.time.Instant;

public record AiConversationResponse(
        String conversationId,
        Long problemId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}

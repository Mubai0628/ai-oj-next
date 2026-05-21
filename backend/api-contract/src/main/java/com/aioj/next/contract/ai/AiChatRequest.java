package com.aioj.next.contract.ai;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(String conversationId, Long problemId, @NotBlank String message) {
}


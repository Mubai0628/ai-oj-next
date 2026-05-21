package com.aioj.next.ai.domain;

public record AiCompletion(String content, String provider, String model, long promptTokens, long completionTokens) {
}

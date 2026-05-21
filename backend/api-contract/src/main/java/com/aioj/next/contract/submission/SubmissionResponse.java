package com.aioj.next.contract.submission;

import java.time.Instant;

public record SubmissionResponse(
        Long id,
        Long problemId,
        Long userId,
        String language,
        SubmissionStatus status,
        String judgeMessage,
        Long timeMillis,
        Long memoryKb,
        Instant createdAt,
        Instant judgedAt
) {
}


package com.aioj.next.contract.submission;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

public record SubmissionResponse(
        Long id,
        Long problemId,
        Long userId,
        String language,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String code,
        SubmissionStatus status,
        String judgeMessage,
        Long timeMillis,
        Long memoryKb,
        Instant createdAt,
        Instant judgedAt
) {
}

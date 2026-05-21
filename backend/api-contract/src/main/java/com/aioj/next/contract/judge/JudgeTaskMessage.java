package com.aioj.next.contract.judge;

public record JudgeTaskMessage(Long submissionId, Long problemId, Long userId, String language, String traceId) {
}


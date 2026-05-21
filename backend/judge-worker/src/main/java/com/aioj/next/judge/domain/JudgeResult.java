package com.aioj.next.judge.domain;

import com.aioj.next.contract.submission.SubmissionStatus;

import java.time.Instant;

public record JudgeResult(SubmissionStatus status, String message, Long timeMillis, Long memoryKb, Instant judgedAt) {
}


package com.aioj.next.judge.domain;

import com.aioj.next.contract.submission.SubmissionStatus;

import java.time.Instant;

public record JudgeResult(
        SubmissionStatus status,
        String message,
        Long timeMillis,
        Long memoryKb,
        Instant judgedAt,
        String stdout,
        String stderr,
        Integer exitStatus,
        Long runTimeMillis
) {
    public static JudgeResult systemError(String message) {
        return new JudgeResult(SubmissionStatus.SYSTEM_ERROR, message,
                0L, 0L, Instant.now(), null, null, null, null);
    }
}

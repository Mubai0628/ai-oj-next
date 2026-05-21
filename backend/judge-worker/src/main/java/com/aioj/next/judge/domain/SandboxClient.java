package com.aioj.next.judge.domain;

import com.aioj.next.contract.judge.JudgeTaskMessage;
import com.aioj.next.contract.submission.SubmissionStatus;
import com.aioj.next.judge.config.JudgeWorkerProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SandboxClient {
    private final JudgeWorkerProperties properties;

    public SandboxClient(JudgeWorkerProperties properties) {
        this.properties = properties;
    }

    public JudgeResult judge(JudgeTaskMessage task) {
        if (!properties.getLanguageWhitelist().contains(task.language())) {
            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled", 0L, 0L, Instant.now());
        }
        return new JudgeResult(SubmissionStatus.ACCEPTED, "Mock sandbox accepted the submission", 12L, 1024L, Instant.now());
    }
}


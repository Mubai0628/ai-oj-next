package com.aioj.next.contract.ai;

import com.aioj.next.contract.problem.TestCaseDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AiChatRequest(
        String conversationId,
        Long problemId,
        @NotBlank String message,
        String mode,
        ProblemContext problemContext,
        CodeContext codeContext
    ) {
    public record ProblemContext(
            String id,
            String title,
            String difficulty,
            String statement,
            String notes,
            List<String> tags,
            List<TestCaseDto> samples,
            Integer timeLimitMillis,
            Integer memoryLimitKb
    ) {
    }

    public record CodeContext(String language, String code) {
    }
}

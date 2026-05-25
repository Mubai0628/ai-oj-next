package com.aioj.next.contract.ai;

import com.aioj.next.contract.problem.TestCaseDto;

import java.time.Instant;
import java.util.List;

public record ProblemDraftResponse(
        Long id,
        String title,
        String difficulty,
        String statement,
        List<String> tags,
        String validationStatus,
        List<String> validationErrors,
        List<TestCaseDto> testCases,
        Integer timeLimitMillis,
        Integer memoryLimitKb,
        Long importedProblemId,
        String model,
        long promptTokens,
        long completionTokens,
        Instant createdAt,
        Long refinedFromDraftId,
        String refineNote
) {
}

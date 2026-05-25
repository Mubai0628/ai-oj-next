package com.aioj.next.contract.ai;

import com.aioj.next.contract.problem.TestCaseDto;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProblemDraftRefineRequest(
        @Size(max = 120) String title,
        @Size(max = 32) String difficulty,
        @Size(max = 20000) String statement,
        List<@Size(max = 40) String> tags,
        List<TestCaseDto> testCases,
        Integer timeLimitMillis,
        Integer memoryLimitKb,
        @Size(max = 500) String refineNote
) {
}

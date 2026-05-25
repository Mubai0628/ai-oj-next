package com.aioj.next.contract.ai;

import jakarta.validation.constraints.NotBlank;

public record ProblemDraftRequest(
        @NotBlank String topic,
        String difficulty,
        String teachingGoal
) {
}

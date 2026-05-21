package com.aioj.next.contract.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmissionCreateRequest(@NotNull Long problemId, @NotBlank String language, @NotBlank String code) {
}


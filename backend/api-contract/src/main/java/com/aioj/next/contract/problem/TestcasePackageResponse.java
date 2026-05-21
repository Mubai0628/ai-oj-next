package com.aioj.next.contract.problem;

import java.time.Instant;
import java.util.List;

public record TestcasePackageResponse(
        Long id,
        Long problemId,
        String version,
        String fileName,
        Long fileSizeBytes,
        String sha256,
        TestcasePackageStatus status,
        boolean active,
        Integer caseCount,
        Integer sampleCount,
        String storageProvider,
        Instant createdAt,
        Instant activatedAt,
        String errorMessage,
        List<TestcasePackageCaseResponse> cases
) {
}

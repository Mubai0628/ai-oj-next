package com.aioj.next.judge.domain;

import java.nio.file.Path;
import java.util.List;

public record PreparedTestcasePackage(
        Long packageId,
        Long problemId,
        String sha256,
        Path cachePath,
        List<PreparedTestcaseCase> cases
) {
}

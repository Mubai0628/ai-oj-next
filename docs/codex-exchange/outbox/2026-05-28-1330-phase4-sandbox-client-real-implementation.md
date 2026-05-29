# Task: Phase 4 — SandboxClient 真实实现 + V7 migration + 前端展示
## Status: done
## Created by: Codex @ 2026-05-28T15:06+08:00
## Linked: inbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md

---

## Report

### Section 1: 调研现状

- `SubmissionEntity` 两份副本当前字段为：`id/problemId/userId/language/code/status/judgeMessage/timeMillis/memoryKb/retryCount/idempotencyKey/createdAt/updatedAt/judgedAt`；本次将 `stdoutExcerpt/stderrExcerpt/exitStatus/runTimeMillis` 插入在 `memoryKb` 与 `retryCount` 之间。
- `JudgeAuditLogEntity` 当前字段为：`id/submissionId/fromStatus/toStatus/workerId/message/createdAt`；本次将 `sandboxRunId/signalValue` 插入在 `message` 与 `createdAt` 之间。
- `TestcasePackageCache.prepareActivePackage(problemId)` 返回 `Optional<PreparedTestcasePackage>`；`PreparedTestcasePackage` 包含 `packageId/problemId/sha256/cachePath/cases`，每个 `PreparedTestcaseCase` 暴露 `inputFile` 与 `expectedOutputFile` 两个本地 `Path`。
- `OutputComparator` 实际签名是 `compare(InputStream expectedOutput, InputStream actualOutput) throws IOException`，返回嵌套 record `ComparisonResult(boolean accepted, String message)`；因此新 `DefaultOutputComparator` 按该接口实现。
- `SubmissionJudgingService.finish()` 原先用 `SubmissionEntity update + LambdaUpdateWrapper` 写 `status/judgeMessage/timeMillis/memoryKb/judgedAt/updatedAt`，现在同一条更新里追加写入 stdout/stderr 摘要、exitStatus、runTimeMillis，并在 Signalled 映射的 `RUNTIME_ERROR` 审计中写 `signalValue`。

### Section 2: V7 migration SQL

```sql
ALTER TABLE submissions
    ADD COLUMN stdout_excerpt TEXT NULL COMMENT 'Truncated stdout from sandbox (max 8KB)',
    ADD COLUMN stderr_excerpt TEXT NULL COMMENT 'Truncated stderr from sandbox (max 8KB)',
    ADD COLUMN exit_status INT NULL COMMENT 'Exit code; for Signalled status this is the signal number',
    ADD COLUMN run_time_millis BIGINT NULL COMMENT 'Wall clock time in milliseconds';

ALTER TABLE judge_audit_logs
    ADD COLUMN sandbox_run_id VARCHAR(64) NULL COMMENT 'go-judge run identifier if applicable',
    ADD COLUMN signal_value INT NULL COMMENT 'POSIX signal value for Signalled status';
```

### Section 3: SandboxClient 主流程关键代码

`judge()` 最终主流程：

```java
public JudgeResult judge(JudgeTaskMessage task) {
    if (!properties.getLanguageWhitelist().contains(task.language())) {
        return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled",
                0L, 0L, Instant.now(), null, null, null, null);
    }
    LangProfile lang;
    try {
        lang = LangProfile.of(task.language());
    } catch (IllegalArgumentException ex) {
        return new JudgeResult(SubmissionStatus.COMPILE_ERROR, ex.getMessage(),
                0L, 0L, Instant.now(), null, null, null, null);
    }

    try {
        SubmissionEntity submission = submissionMapper.selectById(task.submissionId());
        if (submission == null) {
            return JudgeResult.systemError("Submission not found: " + task.submissionId());
        }
        String sourceCode = submission.getCode();
        if (!StringUtils.hasText(sourceCode)) {
            return JudgeResult.systemError("Submission source code is empty");
        }

        Optional<PreparedTestcasePackage> preparedPackage = testcaseCache.prepareActivePackage(task.problemId());
        if (preparedPackage.isEmpty()) {
            return JudgeResult.systemError("No active testcase package");
        }
        PreparedTestcasePackage testcasePackage = preparedPackage.get();
        if (testcasePackage.cases().isEmpty()) {
            return JudgeResult.systemError("No testcase available");
        }

        long cpuLimitNs = millisToNanos(nonNullOrDefault(task.timeLimitMillis(), DEFAULT_TIME_LIMIT_MILLIS));
        long memoryLimitBytes = kbToBytes(nonNullOrDefault(task.memoryLimitKb(), DEFAULT_MEMORY_LIMIT_KB));
        String compiledFileId = null;
        try {
            if (lang.requiresCompile()) {
                CompileOutcome compile = compileSource(lang, sourceCode, safeMultiply(cpuLimitNs, 10),
                        safeMultiply(memoryLimitBytes, 2));
                if (compile.failed()) {
                    return new JudgeResult(SubmissionStatus.COMPILE_ERROR, compile.message(),
                            compile.timeMillis(), compile.memoryKb(), Instant.now(),
                            null, compile.stderr(), compile.exitStatus(), compile.runTimeMillis());
                }
                compiledFileId = compile.fileId();
            }

            long maxTimeMs = 0L;
            long maxMemoryKb = 0L;
            long maxRunTimeMs = 0L;
            for (PreparedTestcaseCase testcase : testcasePackage.cases()) {
                CaseRunOutcome outcome = runCase(lang, sourceCode, compiledFileId, testcase, cpuLimitNs,
                        memoryLimitBytes);
                maxTimeMs = Math.max(maxTimeMs, nullToZero(outcome.timeMillis()));
                maxMemoryKb = Math.max(maxMemoryKb, nullToZero(outcome.memoryKb()));
                maxRunTimeMs = Math.max(maxRunTimeMs, nullToZero(outcome.runTimeMillis()));
                if (outcome.terminalStatus() != SubmissionStatus.ACCEPTED) {
                    return new JudgeResult(outcome.terminalStatus(), outcome.message(),
                            outcome.timeMillis(), outcome.memoryKb(), Instant.now(),
                            outcome.stdout(), outcome.stderr(), outcome.exitStatus(), outcome.runTimeMillis());
                }
            }
            return new JudgeResult(SubmissionStatus.ACCEPTED, "Accepted",
                    maxTimeMs, maxMemoryKb, Instant.now(), null, null, null, maxRunTimeMs);
        } finally {
            if (compiledFileId != null) {
                try {
                    deleteCachedFile(compiledFileId);
                } catch (RuntimeException ex) {
                    log.warn("Failed to cleanup sandbox fileId={}: {}", compiledFileId, ex.getMessage());
                }
            }
        }
    } catch (RuntimeException | IOException ex) {
        log.warn("Sandbox execution failed for submission={}", task.submissionId(), ex);
        return JudgeResult.systemError("Sandbox execution failed: " + safeMessage(ex));
    }
}
```

`LangProfile`：

```java
private record LangProfile(
        boolean requiresCompile,
        List<String> compileArgs,
        String sourceFileName,
        String executableName,
        List<String> runArgs,
        List<String> envVars
) {
    static LangProfile of(String lang) {
        return switch (lang) {
            case "cpp" -> new LangProfile(true,
                    List.of("/usr/bin/g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"),
                    "main.cpp", "main",
                    List.of("./main"),
                    List.of("PATH=/usr/bin:/bin"));
            case "java" -> new LangProfile(true,
                    List.of("/usr/bin/javac", "Main.java"),
                    "Main.java", "Main.class",
                    List.of("/usr/bin/java", "-cp", ".", "Main"),
                    List.of("PATH=/usr/bin:/bin"));
            case "python" -> new LangProfile(false,
                    List.of(), "main.py", null,
                    List.of("/usr/bin/python3", "main.py"),
                    List.of("PATH=/usr/bin:/bin"));
            default -> throw new IllegalArgumentException("Unsupported language: " + lang);
        };
    }
}
```

status mapping：

```java
private SubmissionStatus mapStatus(String goJudgeStatus, List<SandboxFileError> fileError) {
    if (fileError != null) {
        for (SandboxFileError error : fileError) {
            if ("CollectSizeExceeded".equals(error.type())) {
                return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
            }
        }
    }
    if (goJudgeStatus == null) {
        return SubmissionStatus.SYSTEM_ERROR;
    }
    return switch (goJudgeStatus) {
        case "Accepted" -> SubmissionStatus.ACCEPTED;
        case "Time Limit Exceeded" -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
        case "Memory Limit Exceeded" -> SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        case "Output Limit Exceeded" -> SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        case "Nonzero Exit Status", "Signalled" -> SubmissionStatus.RUNTIME_ERROR;
        case "File Error", "Internal Error" -> SubmissionStatus.SYSTEM_ERROR;
        default -> SubmissionStatus.SYSTEM_ERROR;
    };
}
```

### Section 4: Status mapping 决策

- go-judge `Accepted` 只表示进程运行成功；之后继续用 `OutputComparator` 比对 stdout 与 expected output，比对失败改为 `WRONG_ANSWER`。
- `fileError` 优先于最终 `status`；只要出现 `CollectSizeExceeded`，整体映射为 `OUTPUT_LIMIT_EXCEEDED`，覆盖 1900 smoke test 中观察到的 OLE+MLE 混合场景。
- 短路策略已实现：第一个非 `ACCEPTED` case 直接返回整体结果，教学 OJ 优先反馈速度；全 AC 时聚合最大 time/memory/runTime。
- `time/runTime` 从 ns 向上取整到 ms，`memory` 从 bytes 向上取整到 KB。
- go-judge `Signalled` 没有独立 signal 字段，本实现把它映射为 `RUNTIME_ERROR`，并通过 message 为 `Signalled` 时把 `exitStatus` 写入 audit 的 `signalValue`。

### Section 5: Focused diffs

```diff
backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql
+ ALTER TABLE submissions ADD COLUMN stdout_excerpt/stderr_excerpt/exit_status/run_time_millis ...
+ ALTER TABLE judge_audit_logs ADD COLUMN sandbox_run_id/signal_value ...
```

```diff
SubmissionResponse.java
+ @JsonInclude(NON_NULL) String stdoutExcerpt
+ @JsonInclude(NON_NULL) String stderrExcerpt
+ @JsonInclude(NON_NULL) Integer exitStatus
+ @JsonInclude(NON_NULL) Long runTimeMillis
```

```diff
problem-service + judge-worker SubmissionEntity.java
+ private String stdoutExcerpt;
+ private String stderrExcerpt;
+ private Integer exitStatus;
+ private Long runTimeMillis;
+ getters/setters for all four fields
```

```diff
JudgeAuditLogEntity.java
+ private String sandboxRunId;
+ private Integer signalValue;
+ getters/setters
```

```diff
SubmissionService.java
- submission.getTimeMillis(), submission.getMemoryKb(), submission.getCreatedAt(), submission.getJudgedAt()
+ submission.getTimeMillis(), submission.getMemoryKb(),
+ includeCode ? submission.getStdoutExcerpt() : null,
+ includeCode ? submission.getStderrExcerpt() : null,
+ includeCode ? submission.getExitStatus() : null,
+ includeCode ? submission.getRunTimeMillis() : null,
+ submission.getCreatedAt(), submission.getJudgedAt()
```

```diff
SubmissionJudgingService.java
+ update.setStdoutExcerpt(truncateOutput(result.stdout()));
+ update.setStderrExcerpt(truncateOutput(result.stderr()));
+ update.setExitStatus(result.exitStatus());
+ update.setRunTimeMillis(result.runTimeMillis());
+ audit(..., signalValue, sandboxRunId)
+ truncateOutput(max 8KB)
```

```diff
DefaultOutputComparator.java
+ implements OutputComparator
+ CRLF/CR -> LF
+ strip per-line trailing tabs/spaces
+ strip final newlines
+ return ComparisonResult(false, "stdout does not match expected output")
```

```diff
SandboxClient.java
- mock ACCEPTED result
+ RestClient POST /run
+ source loaded from submissions.code
+ testcaseCache.prepareActivePackage(problemId)
+ optional compile step with copyOutCached / fileId cleanup
+ per-case run and stdout comparison
+ status/fileError mapping and ns/bytes unit conversion
```

```diff
packages/api-client/src/index.ts
+ stdoutExcerpt?: string | null;
+ stderrExcerpt?: string | null;
+ exitStatus?: number | null;
+ runTimeMillis?: number | null;
```

```diff
SubmissionDetailModal.vue
+ runTimeMillis and exitStatus meta cells rendered only when non-null
+ stderrExcerpt rendered as plain <pre>, not HTML
```

```diff
packages/i18n/src/messages.ts
+ submissions.viewRunTimeLabel
+ submissions.viewExitStatusLabel
+ submissions.viewStderrLabel
+ zh-CN / en-US added synchronously
```

```diff
apps/web-user/src/styles.css
+ .submission-detail-stderr
+ .submission-detail-stderr-body
```

```diff
docs/HANDOVER.md / docs/deployment.md
+ Phase 4 marked done
+ Phase 5 described as multi-language runtime image + E2E follow-up
+ DefaultOutputComparator.java and V7 migration added to anchors
```

### Section 6: git status --short

本任务新增/修改的文件：

```text
?? backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql
 M backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionResponse.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/SubmissionEntity.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/SubmissionEntity.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java
?? backend/judge-worker/src/main/java/com/aioj/next/judge/domain/DefaultOutputComparator.java
 M packages/api-client/src/index.ts
 M packages/i18n/src/messages.ts
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue (baseline untracked; Phase 4 edited this working-tree file)
 M apps/web-user/src/styles.css
 M docs/HANDOVER.md
 M docs/deployment.md
 M docs/codex-exchange/inbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md
?? docs/codex-exchange/outbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md
```

既有 baseline dirty files 保留，包含 web-admin drawer/alert/testcase manifest、web-user submissions tab/modal、Phase 1/2/3 sandbox 基础设施、exchange README/inbox/outbox 等前序任务文件；未执行 revert、stage、commit、push。

当前 task 文件相对 HEAD 的 `git diff --stat`（注意：同一文件中可能包含前序 baseline 改动）：

```text
12 files changed, 831 insertions(+), 17 deletions(-)
new untracked task files: V7 migration, DefaultOutputComparator.java, this outbox
```

### Section 7: compile / typecheck outputs

```text
=== api-contract ===
[no output; exit 0]
exit=0
=== common-lib ===
[no output; exit 0]
exit=0
=== problem-service ===
[no output; exit 0]
exit=0
=== judge-worker ===
[no output; exit 0]
exit=0
=== web-user typecheck ===
> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
exit=0
=== web-admin typecheck ===
> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
exit=0
```

Commands run:

```powershell
mvn -f backend/pom.xml -pl api-contract -am compile -q
mvn -f backend/pom.xml -pl common-lib -am compile -q
mvn -f backend/pom.xml -pl problem-service -am compile -q
mvn -f backend/pom.xml -pl judge-worker -am compile -q
npm run typecheck -w @aioj/web-user
npm run typecheck -w @aioj/web-admin
```

### Section 8: Manual verification checklist

```text
[ ] Flyway migrate V7 成功（看 problem-service / judge-worker 启动日志）
[ ] submissions 表新加 4 列、judge_audit_logs 表新加 2 列（SHOW COLUMNS）
[ ] 学生端提交一个 Python "print('hello')" → 期望 ACCEPTED + maxTime/maxMemory > 0
[ ] 学生端提交一个 Python "while True: pass" → 期望 TIME_LIMIT_EXCEEDED
[ ] 学生端提交一个 Python "import sys; sys.exit(7)" → 期望 RUNTIME_ERROR + exitStatus=7
[ ] 学生端提交一个 C++ 语法错误 → 期望 COMPILE_ERROR + stderrExcerpt 含编译报错
[ ] 详情弹窗：runTime / exitStatus / stderr 三个字段在非空时正确显示
```

### Section 9: 5-sentence Chinese summary

本次新增 V7 migration，用两条 `ALTER TABLE` 分别给 `submissions` 和 `judge_audit_logs` 增加沙箱 stdout/stderr、exit status、wall time 与 audit signal 字段，遵守“不在 V7 混入 index DDL”的 Flyway 约束。`SandboxClient` 已从 mock 改为真实调用 go-judge `/run`：按语言 profile 编译或直接运行，读取本地 testcase cache 的 stdin/expected output，逐 case 比对并在首个失败 case 短路。状态映射以 `fileError.CollectSizeExceeded` 优先，Accepted 后仍做 stdout 比对，避免把输出错误误判为 AC。当前 task 文件相对 HEAD 的 diff 统计为 831 行新增、17 行删除，另有 V7 migration、DefaultOutputComparator 和 outbox 3 个新增文件；其中 `styles.css/i18n/docs` 统计可能包含前序 baseline 在同文件上的未提交改动。Phase 5 还剩生产级 multi-language runtime image 与真实端到端联调，尤其要验证 Python/C++/Java runtime、Flyway V7 迁移和学生端提交详情展示。

## Next-action hint

- Phase 5 应优先把 sandbox image 固化为包含 `/usr/bin/python3`、`/usr/bin/g++`、`/usr/bin/javac`、`/usr/bin/java` 的可复现镜像，再跑真实提交闭环。
- Java 目前按 `Main.java -> Main.class` 单文件模型执行，若后续支持多 public class / inner class，需要扩展 copyOutCached 文件集合。

# Task: Phase 4 — SandboxClient 真实实现 + V7 migration + 前端展示
## Status: done
## Created by: Claude @ 2026-05-28T13:30+08:00
## Linked: outbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md

---

## Prompt

ROLE
You are Codex executing **Phase 4 of 5** for the sandbox feature.
**这是改动最大的一步**，改动 10+ 文件。Read this entire file. When done,
write the report to
`docs/codex-exchange/outbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

WHY PHASE 4 NOW
Phase 1-3 + 1900 smoke test 已经搭好沙箱基础设施、扩展了 DTO/枚举、
解决了 testcase 跨主机分发。Phase 4 做最后的"真实判题逻辑"——把 Mock
SandboxClient 换成真实调 go-judge `/run`，加 V7 DB migration 落库
新字段，前端展示。

完成后系统**编译完整 + 逻辑闭环**（除了多语言 runtime image，那是
Phase 5）。

DECISIONS LOCKED (1900 outbox 实测 + Phase 3 决策)
- go-judge endpoint: `POST {sandboxEndpoint}/run`
- 鉴权: `Authorization: Bearer ${SANDBOX_TOKEN}`（如配置了）
- 单位换算: time/runTime ns → ms (ceiling)；memory bytes → KB (ceiling)
- 输出截断: stdout/stderr 最多 8 KB 落库（DB TEXT 字段够，但避免膨胀）
- 短路策略: 任一 case 非 AC 立即终止聚合（教学 OJ 优先速度）
- 编译产物缓存: copyOutCached 拿 fileIds.<name>，下个 cmd 用
  `{"fileId": "<id>"}` 引用，最后 `DELETE /file/<id>` 释放

CONSTRAINTS (hard)
- 改动**仅限**列在 "Files in scope" 里的文件
- **V7 migration 严格遵守 Flyway 教训**（HANDOVER §5 提的 V6 半成功事故）：
  - V7 SQL 只用 **单条 ALTER TABLE 多 ADD COLUMN**（MySQL 单 ALTER 原子）
  - **不**在 V7 里加 CREATE INDEX（多 DDL 容易半成功，需要单独 V8 加）
- 不引入新 maven / npm 依赖（Spring Boot 自带 RestClient 已够）
- 不动其他 phase 已经稳定的部分（Phase 2 blob endpoint / Phase 3 enum
  扩展 / Phase 1 sandbox compose）
- 不 commit / stage / push

============================================================
TASK 1 — 调研现状（read-only，写到 outbox Section 1）
============================================================

读以下文件，在 outbox 报告关键现状：

- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/SubmissionEntity.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java`
- `backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/SubmissionEntity.java`（如果是 worker 副本则两边都看）
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java`（重点：返回的 PreparedTestcasePackage / PreparedTestcaseCase 结构和能拿到的 case stdin/expected_output 路径）
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/PreparedTestcaseCase.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/OutputComparator.java` + `ComparisonResult.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/consumer/JudgeTaskListener.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/mapper/SubmissionMapper.java`（是否有读 code 的方法）
- `backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionResponse.java`
- `backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java#toResponse()`

输出到 outbox **Section 1: "调研现状"**：
- SubmissionEntity 现有字段 + 新加字段在哪个位置插入
- JudgeAuditLogEntity 现有字段
- TestcasePackageCache 返回的 case 数据结构（具体如何拿 stdin 文件路径 / expected output 文件路径）
- OutputComparator 接口签名
- SubmissionJudgingService.finish() 当前怎么用 JudgeResult 写库

============================================================
TASK 2 — V7 migration
============================================================

新建 `backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql`：

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

**严格只有两条 ALTER TABLE**（一条改 submissions、一条改 judge_audit_logs）。
不加 INDEX，不加其他 DDL。MySQL ALTER TABLE 单语句多 ADD COLUMN 是
原子的，安全。

============================================================
TASK 3 — Entity 同步加字段
============================================================

### 3.1 SubmissionEntity（注意 problem-service 和 judge-worker **两份副本**都要改）

两个文件：
- `backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/SubmissionEntity.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/SubmissionEntity.java`

**两份都加**相同 4 字段（与 V7 列对应）：

```java
private String stdoutExcerpt;
private String stderrExcerpt;
private Integer exitStatus;
private Long runTimeMillis;
```

加配套 getter/setter。MyBatis-Plus 默认驼峰→下划线映射：
`stdoutExcerpt ↔ stdout_excerpt`✓，无需 `@TableField` 注解。

### 3.2 JudgeAuditLogEntity

`backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java`

加 2 字段：

```java
private String sandboxRunId;
private Integer signalValue;
```

加 getter/setter。

============================================================
TASK 4 — SubmissionResponse DTO + api-client + 前端 type 同步
============================================================

### 4.1 SubmissionResponse 加 4 字段

`backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionResponse.java`

在 `judgedAt` 之前追加（保持顺序，所有字段 nullable，加 `@JsonInclude(NON_NULL)`
让 null 字段不序列化——前端拿到的 list 不带这些字段，只有 detail 才有）：

```java
public record SubmissionResponse(
        Long id,
        Long problemId,
        Long userId,
        String language,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        SubmissionStatus status,
        String judgeMessage,
        Long timeMillis,
        Long memoryKb,
        // ── Phase 4 新增（仅 detail 接口返回）──
        @JsonInclude(JsonInclude.Include.NON_NULL) String stdoutExcerpt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String stderrExcerpt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer exitStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long runTimeMillis,
        Instant createdAt,
        Instant judgedAt
) {
}
```

### 4.2 SubmissionService.toResponse() 适配

`backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java`

`toResponse(submission, includeCode)` 中：

- `includeCode = true`（detail 接口）：把 entity 的 4 个新字段填入 response
- `includeCode = false`（list 接口）：4 个新字段填 `null`（保持列表轻量）

### 4.3 api-client TS type 加字段

`packages/api-client/src/index.ts` 的 `SubmissionResponse` interface：

```ts
export interface SubmissionResponse {
  id: EntityId;
  problemId: EntityId;
  userId: EntityId;
  language: string;
  code?: string | null;
  status: SubmissionStatus;
  judgeMessage: string;
  timeMillis?: number;
  memoryKb?: number;
  // ── Phase 4 新增 ──
  stdoutExcerpt?: string | null;
  stderrExcerpt?: string | null;
  exitStatus?: number | null;
  runTimeMillis?: number | null;
  createdAt: string;
  judgedAt?: string;
}
```

============================================================
TASK 5 — SubmissionJudgingService 写入新字段
============================================================

`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java`

`finish(JudgeTaskMessage task, JudgeResult result)` 当前用 `LambdaUpdateWrapper`
更新几个字段。**追加** 4 个新字段写入（基于 JudgeResult Phase 3 加的新
component）：

```java
public boolean finish(JudgeTaskMessage task, JudgeResult result) {
    SubmissionStatus status = result.status();
    Instant judgedAt = result.judgedAt() == null ? Instant.now() : result.judgedAt();
    SubmissionEntity update = new SubmissionEntity();
    update.setStatus(status);
    update.setJudgeMessage(truncate(result.message()));
    update.setTimeMillis(result.timeMillis());
    update.setMemoryKb(result.memoryKb());
    update.setJudgedAt(judgedAt);
    update.setUpdatedAt(judgedAt);
    // ── Phase 4 新增字段 ──
    update.setStdoutExcerpt(truncateOutput(result.stdout()));
    update.setStderrExcerpt(truncateOutput(result.stderr()));
    update.setExitStatus(result.exitStatus());
    update.setRunTimeMillis(result.runTimeMillis());
    // ...其余原逻辑不变...
}

private static final int OUTPUT_MAX_LENGTH = 8 * 1024;

private static String truncateOutput(String value) {
    if (value == null) return null;
    if (value.length() <= OUTPUT_MAX_LENGTH) return value;
    return value.substring(0, OUTPUT_MAX_LENGTH) + "\n...[truncated]";
}
```

audit 写入也扩展：在 `audit()` 私有方法签名改造为支持新字段，但**最小
改动**：在 finish() 里调用 audit 时如果是 Signalled 状态，把
exitStatus 作为 signal_value 一并塞进去。具体方式：

- 扩 `audit()` 方法签名加 2 个 nullable 参数 `Integer signalValue,
  String sandboxRunId`，或新增一个 `auditWithExtras()` 方法
- finish() 调用时：如果 `status == SIGNALLED`（注意：项目枚举里没有
  SIGNALLED，而是 go-judge "Signalled" 映射到 `RUNTIME_ERROR`；
  signalValue 取 result.exitStatus()）→ 传 signal_value；其他 null
- sandboxRunId 留 null（go-judge 没有 server-side run id，将来对接
  其他沙箱再填）

============================================================
TASK 6 — DefaultOutputComparator 实现
============================================================

新建 `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/DefaultOutputComparator.java`：

```java
package com.aioj.next.judge.domain;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DefaultOutputComparator implements OutputComparator {

    private static final Pattern TRAILING_WS = Pattern.compile("[\\s]+$", Pattern.MULTILINE);

    @Override
    public ComparisonResult compare(String actual, String expected) {
        if (actual == null && expected == null) return ComparisonResult.match();
        if (actual == null || expected == null) {
            return ComparisonResult.mismatch("one side is null");
        }
        // 行尾空白去除 + 末尾换行归一化
        String normalizedActual = normalize(actual);
        String normalizedExpected = normalize(expected);
        if (normalizedActual.equals(normalizedExpected)) {
            return ComparisonResult.match();
        }
        return ComparisonResult.mismatch("stdout does not match expected output");
    }

    private static String normalize(String text) {
        // 1. 转换 \r\n / \r → \n
        String unified = text.replace("\r\n", "\n").replace("\r", "\n");
        // 2. 去掉每行行尾空白
        String trimmed = TRAILING_WS.matcher(unified).replaceAll("");
        // 3. 去掉末尾连续换行
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '\n') end--;
        return trimmed.substring(0, end);
    }
}
```

⚠️ `ComparisonResult.match()` / `mismatch(reason)` 是已有的 sealed
类工厂方法，按现有 `ComparisonResult.java` 实际 API 调整。如果工厂
方法名不一样（如 `accepted()` / `rejected()`），用实际名字。

============================================================
TASK 7 — SandboxClient 真实实现（核心）
============================================================

重写 `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java`。

### 7.1 依赖注入

```java
@Component
public class SandboxClient {

    private static final Logger log = LoggerFactory.getLogger(SandboxClient.class);
    private static final int STDOUT_COLLECT_LIMIT = 65536;  // 64 KB collector
    private static final int STDERR_COLLECT_LIMIT = 65536;

    private final JudgeWorkerProperties properties;
    private final TestcasePackageCache testcaseCache;
    private final SubmissionMapper submissionMapper;
    private final OutputComparator outputComparator;
    private final RestClient restClient;

    public SandboxClient(JudgeWorkerProperties properties,
                         TestcasePackageCache testcaseCache,
                         SubmissionMapper submissionMapper,
                         OutputComparator outputComparator) {
        this.properties = properties;
        this.testcaseCache = testcaseCache;
        this.submissionMapper = submissionMapper;
        this.outputComparator = outputComparator;
        var builder = RestClient.builder().baseUrl(stripTrailingRun(properties.getSandboxEndpoint()));
        if (StringUtils.hasText(properties.getSandboxToken())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getSandboxToken());
        }
        this.restClient = builder.build();
    }

    /** sandboxEndpoint 默认 ".../execute"；去除尾部 path 让 baseUrl 只是 host */
    private static String stripTrailingRun(String endpoint) {
        // e.g. "http://sandbox:8090/execute" → "http://sandbox:8090"
        if (endpoint == null) return "http://localhost:8090";
        int idx = endpoint.lastIndexOf("/");
        return idx > "http://".length() ? endpoint.substring(0, idx) : endpoint;
    }
}
```

### 7.2 judge(task) 主流程

```java
public JudgeResult judge(JudgeTaskMessage task) {
    // 1. 语言白名单
    if (!properties.getLanguageWhitelist().contains(task.language())) {
        return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled",
                0L, 0L, Instant.now(), null, null, null, null);
    }

    // 2. 读源码（worker 跨机直连 MySQL）
    SubmissionEntity submission = submissionMapper.selectById(task.submissionId());
    if (submission == null) {
        return JudgeResult.systemError("Submission not found: " + task.submissionId());
    }
    String sourceCode = submission.getCode();
    if (!StringUtils.hasText(sourceCode)) {
        return JudgeResult.systemError("Submission source code is empty");
    }

    // 3. 拿测试包（含编译命令所需 / 用例文件路径）
    PreparedTestcasePackage pkg;
    try {
        pkg = testcaseCache.prepareActivePackage(task.problemId());
    } catch (TestcasePackageUnavailableException ex) {
        return JudgeResult.systemError("Testcase package unavailable: " + ex.getMessage());
    }
    if (pkg.cases().isEmpty()) {
        return JudgeResult.systemError("No testcase available");
    }

    // 4. 资源限额（优先 task snapshot；fallback 用 worker properties 默认）
    long cpuLimitNs = millisToNs(nonNullOrDefault(task.timeLimitMillis(), 1000));
    long memoryLimitBytes = kbToBytes(nonNullOrDefault(task.memoryLimitKb(), 262144L));

    // 5. 编译阶段（C++/Java）
    LangProfile lang = LangProfile.of(task.language());  // 见下方 helper
    String compiledFileId = null;
    if (lang.requiresCompile()) {
        CompileOutcome compile = compileSource(lang, sourceCode, cpuLimitNs * 10, memoryLimitBytes * 2);
        if (compile.failed()) {
            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, compile.message(),
                    compile.timeMillis(), compile.memoryKb(), Instant.now(),
                    null, compile.stderr(), compile.exitStatus(), compile.runTimeMillis());
        }
        compiledFileId = compile.fileId();
    }

    // 6. per-case 循环
    try {
        long maxTimeMs = 0;
        long maxMemoryKb = 0;
        long maxRunTimeMs = 0;
        for (PreparedTestcaseCase caseItem : pkg.cases()) {
            CaseRunOutcome outcome = runCase(lang, sourceCode, compiledFileId,
                    caseItem, cpuLimitNs, memoryLimitBytes);
            maxTimeMs = Math.max(maxTimeMs, outcome.timeMillis());
            maxMemoryKb = Math.max(maxMemoryKb, outcome.memoryKb());
            maxRunTimeMs = Math.max(maxRunTimeMs, outcome.runTimeMillis() == null ? 0 : outcome.runTimeMillis());
            if (outcome.terminalStatus() != SubmissionStatus.ACCEPTED) {
                // 短路：第一个失败 case 决定整体状态
                return new JudgeResult(outcome.terminalStatus(), outcome.message(),
                        outcome.timeMillis(), outcome.memoryKb(), Instant.now(),
                        outcome.stdout(), outcome.stderr(), outcome.exitStatus(), outcome.runTimeMillis());
            }
        }
        // 全 AC
        return new JudgeResult(SubmissionStatus.ACCEPTED, "Accepted",
                maxTimeMs, maxMemoryKb, Instant.now(),
                null, null, null, maxRunTimeMs);
    } finally {
        // 7. cleanup 编译产物 fileId
        if (compiledFileId != null) {
            try {
                deleteCachedFile(compiledFileId);
            } catch (RuntimeException ex) {
                log.warn("Failed to cleanup sandbox fileId={}: {}", compiledFileId, ex.getMessage());
            }
        }
    }
}
```

### 7.3 LangProfile / 编译 / 运行命令

```java
private record LangProfile(
        String language,
        boolean requiresCompile,
        List<String> compileArgs,    // ["g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"]
        String sourceFileName,        // "main.cpp" / "Main.java" / "main.py"
        String executableName,        // "main" / "Main.class" / null (python)
        List<String> runArgs,         // ["./main"] / ["/usr/bin/java","-cp",".","Main"] / ["/usr/bin/python3","main.py"]
        List<String> envVars
) {
    static LangProfile of(String lang) {
        return switch (lang) {
            case "cpp" -> new LangProfile("cpp", true,
                    List.of("/usr/bin/g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"),
                    "main.cpp", "main",
                    List.of("./main"),
                    List.of("PATH=/usr/bin:/bin"));
            case "java" -> new LangProfile("java", true,
                    List.of("/usr/bin/javac", "Main.java"),
                    "Main.java", "Main.class",
                    List.of("/usr/bin/java", "-cp", ".", "Main"),
                    List.of("PATH=/usr/bin:/bin"));
            case "python" -> new LangProfile("python", false,
                    List.of(), "main.py", null,
                    List.of("/usr/bin/python3", "main.py"),
                    List.of("PATH=/usr/bin:/bin"));
            default -> throw new IllegalArgumentException("Unsupported language: " + lang);
        };
    }
}
```

### 7.4 compileSource / runCase 实现

两个方法都用 `RestClient.post("/run").body(...).retrieve().toEntity(...)` 调
go-judge。请求 body 结构（详见 1900 outbox Section 5）：

**编译请求**：

```json
{
  "cmd": [{
    "args": ["/usr/bin/g++", "-O2", "-std=c++17", "main.cpp", "-o", "main"],
    "env": ["PATH=/usr/bin:/bin"],
    "files": [
      {"content": ""},
      {"name": "stdout", "max": 65536},
      {"name": "stderr", "max": 65536}
    ],
    "cpuLimit": <compileTimeNs>,
    "memoryLimit": <compileMemoryBytes>,
    "procLimit": 50,
    "copyIn": {"main.cpp": {"content": "<source>"}},
    "copyOutCached": ["main"]
  }]
}
```

期望响应：
- `status == "Accepted"` 且 `fileIds.main` 有值 → 编译成功，存 fileId
- `status != "Accepted"` → 编译失败，把 `files.stderr`（编译报错信息）
  作为 message 返给前端

**运行请求**：

```json
{
  "cmd": [{
    "args": <runArgs>,
    "env": <envVars>,
    "files": [
      {"content": "<case stdin content>"},
      {"name": "stdout", "max": 65536},
      {"name": "stderr", "max": 65536}
    ],
    "cpuLimit": <cpuLimitNs>,
    "memoryLimit": <memoryLimitBytes>,
    "procLimit": 50,
    "copyIn": {<executableName>: {"fileId": "<compiledFileId>"}}
  }]
}
```

Python 不需要 fileId，但需要 `copyIn: {"main.py": {"content": "<source>"}}`。

### 7.5 deleteCachedFile

```java
private void deleteCachedFile(String fileId) {
    restClient.delete().uri("/file/{id}", fileId).retrieve().toBodilessEntity();
}
```

### 7.6 status mapping（关键）

```java
private SubmissionStatus mapStatus(String goJudgeStatus, List<Map<String, Object>> fileError) {
    // 优先看 fileError——unbounded output 场景 final status 可能是 MLE 但 fileError
    // 已记录 CollectSizeExceeded（参考 1900 outbox Section 9 实测）
    if (fileError != null && !fileError.isEmpty()) {
        for (Map<String, Object> fe : fileError) {
            Object type = fe.get("type");
            if ("CollectSizeExceeded".equals(type)) {
                return SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
            }
        }
    }
    return switch (goJudgeStatus) {
        case "Accepted" -> SubmissionStatus.ACCEPTED;
        case "Time Limit Exceeded" -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
        case "Memory Limit Exceeded" -> SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        case "Output Limit Exceeded" -> SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
        case "Nonzero Exit Status" -> SubmissionStatus.RUNTIME_ERROR;
        case "Signalled" -> SubmissionStatus.RUNTIME_ERROR;
        case "File Error" -> SubmissionStatus.SYSTEM_ERROR;
        case "Internal Error" -> SubmissionStatus.SYSTEM_ERROR;
        default -> SubmissionStatus.SYSTEM_ERROR;
    };
}
```

⚠️ Accepted 后还要走 stdout 比对——如果 OutputComparator.compare(actual,
expected) 失败 → 状态改为 WRONG_ANSWER。

### 7.7 case stdin / expected output 读取

PreparedTestcaseCase 应该提供 case 文件路径。在 worker 主机本地 cache
里读 stdin / expected output 文件（Phase 2 已经保证 zip 解压到本地
cache）。

具体怎么读看 TASK 1 调研到的 PreparedTestcaseCase 接口。可能是
`caseItem.inputPath()` / `caseItem.expectedOutputPath()` 返回
`Path`，用 `Files.readString(path)` 即可。

### 7.8 sandbox 故障重试（最小策略）

本 Phase 不做重试/熔断。go-judge 5xx 直接 → SYSTEM_ERROR。Phase 5 联
调时再决定重试策略。

============================================================
TASK 8 — 前端 SubmissionDetailModal 展示新字段
============================================================

`apps/web-user/src/components/submission/SubmissionDetailModal.vue`

在现有 meta grid（题目/语言/状态/耗时/内存）之后追加（只在字段非 null
时展示，避免空字段污染 UI）：

```vue
<div v-if="detail.runTimeMillis != null">
  <span>{{ t('submissions.viewRunTimeLabel') }}</span>
  <strong>{{ detail.runTimeMillis }} ms</strong>
</div>
<div v-if="detail.exitStatus != null">
  <span>{{ t('submissions.viewExitStatusLabel') }}</span>
  <strong>{{ detail.exitStatus }}</strong>
</div>
```

在 stdin/judgeMessage 提示框之后追加 stderr 块（只在非空时）：

```vue
<div v-if="detail.stderrExcerpt" class="submission-detail-stderr">
  <strong>{{ t('submissions.viewStderrLabel') }}</strong>
  <pre class="submission-detail-stderr-body">{{ detail.stderrExcerpt }}</pre>
</div>
```

新增 styles.css 段：

```css
.submission-detail-stderr {
  margin-top: 12px;
}
.submission-detail-stderr-body {
  max-height: 200px;
  overflow: auto;
  padding: 8px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}
```

⚠️ stdout/stderr 是沙箱原始输出，可能含 ANSI / 控制字符。**不渲染为
HTML**——用 `<pre>` 保留原文即可。

i18n 新增（zh-CN + en-US 同步）：

| key | zh-CN | en-US |
|---|---|---|
| `viewRunTimeLabel` | 实际耗时 | Wall time |
| `viewExitStatusLabel` | 退出码 | Exit status |
| `viewStderrLabel` | 错误输出 (stderr) | Standard error |

============================================================
TASK 9 — compile + typecheck 验收
============================================================

按顺序跑，每步 exit 0：

```bash
mvn -f backend/pom.xml -pl api-contract -am compile -q
mvn -f backend/pom.xml -pl common-lib -am compile -q
mvn -f backend/pom.xml -pl problem-service -am compile -q
mvn -f backend/pom.xml -pl judge-worker -am compile -q
npm run typecheck -w @aioj/web-user
npm run typecheck -w @aioj/web-admin
```

============================================================
TASK 10 — HANDOVER + deployment 更新
============================================================

`docs/HANDOVER.md`：
- §3 进行中：Phase 4 done
- §4 待办：勾掉"真实隔离沙箱" P0 → 只剩 Phase 5 (multi-language image)
- §6 关键文件锚点：加 `DefaultOutputComparator.java`、V7 migration

`docs/deployment.md`（如有相关章节）：
- 说明 Phase 4 完成；Phase 5 multi-language runtime image 是最后一公里

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED 改动集合（核心 10+ 文件）：
     ?? backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql
     M  backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionResponse.java
     M  backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/SubmissionEntity.java
     M  backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/SubmissionEntity.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java
     ?? backend/judge-worker/src/main/java/com/aioj/next/judge/domain/DefaultOutputComparator.java
     M  packages/api-client/src/index.ts
     M  packages/i18n/src/messages.ts
     M  apps/web-user/src/components/submission/SubmissionDetailModal.vue
     M  apps/web-user/src/styles.css
     M  docs/HANDOVER.md
     M  docs/codex-exchange/inbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md
     ?? docs/codex-exchange/outbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md
   既有 baseline dirty files 保留。**不应**修改 Phase 1 deploy/ /
   Phase 2 internal endpoint / Phase 3 enum 文件。

2. **Section "调研现状"**（TASK 1 输出）

3. **Section "V7 migration SQL"**：完整 SQL 全文

4. **Section "SandboxClient 主流程关键代码"**：贴 judge() 方法的最终
   实现 + LangProfile + status mapping 三段（其余 helper 省略）

5. **Section "Status mapping 决策"**：明确说明
   - go-judge "Accepted" + stdout 比对失败 → WRONG_ANSWER
   - fileError 含 CollectSizeExceeded → OUTPUT_LIMIT_EXCEEDED（覆盖原 status）
   - 短路策略：第一个非 AC case 决定整体状态

6. Focused diffs（~15 lines context）for each modified file

7. compile / typecheck outputs（最后 5 行 each）

8. Manual verification checklist（人工后续做，Codex 不跑）：
   ```
   [ ] Flyway migrate V7 成功（看 problem-service / judge-worker 启动日志）
   [ ] submissions 表新加 4 列、judge_audit_logs 表新加 2 列（SHOW COLUMNS）
   [ ] 学生端提交一个 Python "print('hello')" → 期望 ACCEPTED + maxTime/maxMemory > 0
   [ ] 学生端提交一个 Python "while True: pass" → 期望 TIME_LIMIT_EXCEEDED
   [ ] 学生端提交一个 Python "import sys; sys.exit(7)" → 期望 RUNTIME_ERROR + exitStatus=7
   [ ] 学生端提交一个 C++ 语法错误 → 期望 COMPILE_ERROR + stderrExcerpt 含编译报错
   [ ] 详情弹窗：runTime / exitStatus / stderr 三个字段在非空时正确显示
   ```

9. **5-sentence Chinese summary** 覆盖：
   - V7 schema 改动
   - SandboxClient 主流程要点
   - status mapping 关键决策（fileError 优先级 / stdout 比对）
   - 改动行数总数
   - Phase 5 还剩什么没做（multi-language runtime image + 端到端联调）

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-28-1330-phase4-sandbox-client-real-implementation.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql` (new)
- `backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionResponse.java` (add 4 fields)
- `backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/SubmissionEntity.java` (add 4 fields)
- `backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java` (toResponse adapter)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/SubmissionEntity.java` (add 4 fields)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java` (add 2 fields)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java` (rewrite)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java` (write new fields + signal_value to audit)
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/DefaultOutputComparator.java` (new)
- `packages/api-client/src/index.ts` (interface)
- `packages/i18n/src/messages.ts` (3 new keys × 2 locales)
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue` (display)
- `apps/web-user/src/styles.css` (stderr block)
- `docs/HANDOVER.md`
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md` Section 5 / 12（go-judge schema + 字段映射）
- 现有 `PreparedTestcaseCase` / `OutputComparator` / `ComparisonResult` 接口
- Phase 3 `JudgeResult` / `JudgeTaskMessage` 新签名

**Hard 禁区**:
- Phase 1 deploy/ 文件
- Phase 2 `InternalTestcaseController` / `TestcaseBlobClient` / `InternalApiTokenFilter`
- Phase 3 已稳定的 `SubmissionStatus` / `JudgeResult` / `JudgeTaskMessage` 字段定义（只用，不改）
- 任何已存在的 V*.sql（只加 V7，不动 V1-V6）
- 新增 maven / npm 依赖
- commit / stage / push

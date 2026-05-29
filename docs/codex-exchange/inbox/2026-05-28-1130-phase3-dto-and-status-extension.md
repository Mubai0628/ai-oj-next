# Task: Phase 3 — DTO + Status enum 扩展（基于 1900 outbox 实测数据）
## Status: done
## Created by: Claude @ 2026-05-28T11:30+08:00
## Linked: outbox/2026-05-28-1130-phase3-dto-and-status-extension.md

---

## Prompt

ROLE
You are Codex executing **Phase 3 of 5** for the sandbox feature. Read
this entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-28-1130-phase3-dto-and-status-extension.md`
per `docs/codex-exchange/README.md`. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`).

WHY PHASE 3 NOW
Phase 1 (sandbox infra) + 1900 minimal smoke test 已经拿到 go-judge
真实响应字段。Phase 4 (SandboxClient 真实实现) 需要先有：

1. `SubmissionStatus` 包含 MLE / OLE 终态
2. `JudgeResult` 字段能承载 stdout / stderr / exitStatus / wall time
3. `JudgeTaskMessage` 带 problem time/memory limit snapshot（让 worker
   跨机时**不必查 problems 表**）
4. 前端 i18n + StatusChip 同步两个新终态

**Phase 3 不动 DB schema**（V7 migration 留到 Phase 4 一次性看清需要
哪些字段再加，避免提前 ALTER 但不用造成浪费）。

DECISIONS LOCKED (from prior conversations + 1900 outbox)
- go-judge 实测 status 集合: Accepted / TLE / Nonzero Exit Status /
  Signalled / MLE / OLE / File Error / Internal Error
- 无独立 signal 字段，signal 值嵌入 exitStatus
- time / runTime 单位 ns，memory 单位 byte；AIOJ 用 ms / KB
- 转换用 ceiling division 防止显示 0
- procPeak / fileErrors 是 nice-to-have，本 Phase 不引入（Phase 4 真
  写入审计时再决定）

CONSTRAINTS (hard)
- 只动 contract + i18n + frontend chip + judge-worker mock 适配
- **不动**：
  - 任何 V*.sql migration（DB 改 Phase 4 一并）
  - SubmissionEntity 表字段（DB 改 Phase 4 一并）
  - TestcasePackageCache / SandboxClient 真实逻辑（Phase 2 / 4 处理）
  - apps/web-* 其他 view（只动 StatusChip 用法）
- 不引入新 npm / maven 依赖
- 不 commit / stage / push

============================================================
TASK 1 — Extend `SubmissionStatus` enum
============================================================

File: `backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionStatus.java`

当前枚举（验证后改）：
```java
public enum SubmissionStatus {
    QUEUED, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR,
    RUNTIME_ERROR, TIME_LIMIT_EXCEEDED, SYSTEM_ERROR
}
```

在 `TIME_LIMIT_EXCEEDED` 之后**追加**两个值（保持枚举顺序与
go-judge 状态分类对齐）：

```java
public enum SubmissionStatus {
    QUEUED, RUNNING, ACCEPTED, WRONG_ANSWER, COMPILE_ERROR,
    RUNTIME_ERROR, TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    OUTPUT_LIMIT_EXCEEDED,
    SYSTEM_ERROR
}
```

⚠️ **不要改变现有枚举的相对顺序**，但 ordinal 变了不影响（项目用 enum
name 而非 ordinal 持久化，看 `submissions.status` 列定义 VARCHAR）。
如果发现哪里依赖 ordinal，标 blocked。

============================================================
TASK 2 — Extend `JudgeResult` record
============================================================

File: `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/JudgeResult.java`

当前：
```java
public record JudgeResult(SubmissionStatus status, String message,
    Long timeMillis, Long memoryKb, Instant judgedAt) {
}
```

改为（**追加字段，全部 nullable**）：
```java
public record JudgeResult(
    SubmissionStatus status,
    String message,
    Long timeMillis,
    Long memoryKb,
    Instant judgedAt,
    // ── Phase 3 新增：沙箱真实输出（all nullable for mock / partial）──
    String stdout,            // 截断后；null 表示未采集
    String stderr,            // 截断后；null 表示未采集
    Integer exitStatus,       // exit code 或 signal 值（go-judge Signalled 时存信号编号）
    Long runTimeMillis        // wall clock time；null 表示未采集
) {
    /** 仅设核心字段、其余 null，用于错误/系统异常等不需要细节的场景。 */
    public static JudgeResult systemError(String message) {
        return new JudgeResult(SubmissionStatus.SYSTEM_ERROR, message,
            0L, 0L, Instant.now(), null, null, null, null);
    }
}
```

适配点：
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java`
  当前的 mock 返回：`new JudgeResult(SubmissionStatus.ACCEPTED, "Mock sandbox accepted the submission", 12L, 1024L, Instant.now())`
  改为补 4 个 null：
  `new JudgeResult(SubmissionStatus.ACCEPTED, "...", 12L, 1024L, Instant.now(), null, null, null, null)`
  另一处 `COMPILE_ERROR` 同理补 null。**保持 mock 行为不变**，只是构造调用对齐新签名。
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java`
  `finish(JudgeTaskMessage task, JudgeResult result)` 用到 `result.status() / message() / timeMillis() / memoryKb() / judgedAt()` 不变。
  **不要**在本 Phase 把 stdout/stderr 写到 audit log（DB 字段 Phase 4
  才加）。Phase 3 字段只在 contract 上存在。

============================================================
TASK 3 — Extend `JudgeTaskMessage` with limits snapshot
============================================================

File: `backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java`

当前：
```java
public record JudgeTaskMessage(Long submissionId, Long problemId,
    Long userId, String language, String traceId) {
}
```

改为（**追加 2 个 nullable 字段**——保留 null 时 Phase 4 还能 fallback
查 problems 表）：

```java
public record JudgeTaskMessage(
    Long submissionId,
    Long problemId,
    Long userId,
    String language,
    String traceId,
    // ── Phase 3 新增：题目限额 snapshot（避免 worker 跨机查 problems 表）──
    Integer timeLimitMillis,    // 来自 problems.time_limit_millis；null 时 worker 应 fallback
    Long memoryLimitKb          // 来自 problems.memory_limit_kb；null 时 worker 应 fallback
) {
}
```

**Backward compatibility 警告**：RabbitMQ 里的旧消息体（5 字段格式）反
序列化到新 record 时缺 2 个字段。Jackson 默认配置可能抛
`MismatchedInputException`。需要：
- Spring Boot RabbitMQ 默认用 `MessageConverter` 是 `SimpleMessageConverter`
  还是 `Jackson2JsonMessageConverter`？读 `judge-worker` 的
  `RabbitConfig.java`（如有）/ `JudgeQueueConfig.java`，确认序列化方式
- 如果用 Jackson + record：record 的反序列化对缺失字段，Jackson 2.16+
  会传 null（如果字段 nullable），早期版本可能抛 missing creator argument
- **安全做法**：在 prompt 报告里说明使用的 converter 和兼容性。**如果
  现有队列里有积压消息**，建议在升级前手动 `docker compose exec
  rabbitmq rabbitmqctl purge_queue ai-oj-next.judge.queue` 清掉，避免
  反序列化失败
- 本 inbox **不要修改 RabbitConfig** —— 仅在 outbox 标注兼容性影响

============================================================
TASK 4 — `problem-service` publish 时填限额
============================================================

File: `backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java`

当前 `submit()` 调用：
```java
publishAfterCommit(new JudgeTaskMessage(submission.getId(),
    request.problemId(), userId, language, TraceIds.current()));
```

需要在 publish 前读到 problem 的 time/memory limit。当前 `submit()`
已经调用 `problemCatalog.existsActive(request.problemId())` —— 把它
扩展或新加一个方法拿出限额：

**做法 A（最简单）**：在 SubmissionService 直接通过 ProblemCatalog 或
现有 mapper 多查一次拿 ProblemEntity（看 `ProblemCatalog` 接口当前
有什么方法；可能有 `findActive(id)` 之类返回 entity / Optional）。

**做法 B**：扩展 `ProblemCatalog.existsActive` 为 `findActiveSnapshot`
返回 `Optional<ProblemLimitSnapshot>` 之类小 record。但这扩了 catalog
接口，需要看其他调用方。

按你看到的 ProblemCatalog 现状选 A 或 B。报告里说明选择 + 改动。

publish 改为：
```java
ProblemEntity problem = problemCatalog.findActive(request.problemId())
    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Problem not found"));
// 注意：上面 existsActive 检查可以替换掉，避免重复查询

publishAfterCommit(new JudgeTaskMessage(
    submission.getId(),
    request.problemId(),
    userId,
    language,
    TraceIds.current(),
    problem.getTimeLimitMillis(),     // Integer
    problem.getMemoryLimitKb()         // Long
));
```

⚠️ ProblemEntity 字段名按实际 — 读 `backend/problem-service/.../entity/ProblemEntity.java`
确认 getter 名字（可能是 `getTimeLimit` / `getMemoryLimit` / 或带后缀）。

============================================================
TASK 5 — `packages/api-client` 同步 SubmissionStatus type
============================================================

File: `packages/api-client/src/index.ts`

当前 `SubmissionStatus` type 定义（line ~4-?）需要加两个值：

```ts
export type SubmissionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'        // ← 新增
  | 'OUTPUT_LIMIT_EXCEEDED'        // ← 新增
  | 'SYSTEM_ERROR';
```

============================================================
TASK 6 — `packages/i18n/messages.ts` 双语文案
============================================================

File: `packages/i18n/src/messages.ts`

在 `submissionStatus.*` 段两处（zh-CN.submissionStatus + en-US.submissionStatus）
同步加：

| key | zh-CN | en-US |
|---|---|---|
| `MEMORY_LIMIT_EXCEEDED` | 内存超限 | Memory Limit Exceeded |
| `OUTPUT_LIMIT_EXCEEDED` | 输出超限 | Output Limit Exceeded |

保持现有 key 顺序，**追加**到 TIME_LIMIT_EXCEEDED 之后（位置一致性
方便后续 grep）。

============================================================
TASK 7 — 前端 StatusChip tone mapping
============================================================

各前端有自己的 `statusTone` 函数（switch / map）。**grep 全仓 `statusTone`
找到所有定义**：

```bash
rg "statusTone|status === 'ACCEPTED'" --type vue --type ts
```

预期命中：
- `apps/web-user/src/views/SubmissionsView.vue` (Codex 之前 read 过)
- `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue`（如有）
- `apps/web-admin/` 类似 submissions 相关组件（如有）
- 可能下沉到 `packages/ui/` 共享组件（如有）

**每处** statusTone 函数 / 颜色 map 都加 MLE/OLE 分支。Tone 建议：

```ts
function statusTone(status: SubmissionStatus): 'primary' | 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'ACCEPTED') return 'success';
  if (status === 'RUNNING') return 'primary';
  if (status === 'QUEUED') return 'neutral';
  if (status === 'COMPILE_ERROR') return 'warning';
  if (status === 'MEMORY_LIMIT_EXCEEDED') return 'warning';   // ← 新增
  if (status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';   // ← 新增
  return 'danger';
}
```

理由：MLE/OLE 是**资源/输出超限**，属于"用户代码问题但不致命"，跟
COMPILE_ERROR 同 tone（warning 橙色）更准。WA/RE/TLE 留 danger（红
色）。

在 outbox **Section "StatusChip audit"** 列出每个 grep 命中的文件 +
你的修改。

============================================================
TASK 8 — typecheck + compile 验收
============================================================

按顺序跑（每步必须 exit 0）：

```bash
# 后端：先 contract 编译，再各服务
mvn -pl backend/api-contract -am compile -q
mvn -pl backend/problem-service -am compile -q
mvn -pl backend/judge-worker -am compile -q

# 前端：i18n / api-client 编译，admin / user typecheck
npm run typecheck -w @aioj/web-user
npm run typecheck -w @aioj/web-admin
```

如果某步失败，**先停下排查**，不要继续往下跑。把错误贴 outbox 标
blocked 并说明卡在哪个 task。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED 修改集合：
     M  backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionStatus.java
     M  backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/domain/JudgeResult.java
     M  backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java
     M  backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java
     M  packages/api-client/src/index.ts
     M  packages/i18n/src/messages.ts
     M  apps/web-user/src/views/SubmissionsView.vue            (StatusChip 用法)
     M  apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue (StatusChip 用法)
     M  apps/web-user/src/components/submission/SubmissionDetailModal.vue (如有)
     M  apps/web-admin/...        (StatusChip 用法 if any)
     M  docs/codex-exchange/inbox/2026-05-28-1130-phase3-dto-and-status-extension.md
     ?? docs/codex-exchange/outbox/2026-05-28-1130-phase3-dto-and-status-extension.md
   既有 baseline dirty files 保留。**不应**出现 V*.sql / SubmissionEntity
   修改。

2. Focused diffs（~15 lines context）for each Java + TS + Vue + ts 文件。
   Java 改动相对小，全 diff 也 ok。

3. compile / typecheck 输出末尾 5 行 each。

4. Section "StatusChip audit"：列每个 grep 命中文件 + tone 增改前后。

5. Section "RabbitMQ compatibility check"：报告
   - 当前用的 MessageConverter（找 `judge-worker/src/main/resources/` 配置 +
     consumer config）
   - 旧消息体在新 record 下反序列化的行为预测
   - 升级建议（清队列 / 兼容设计）

6. 5-sentence Chinese summary：
   - 枚举/DTO 改动数
   - 前端 chip mapping 改动数 + 选的 tone
   - problem-service publish 改造（A 或 B 方案，为什么）
   - RabbitMQ 兼容性结论
   - Phase 4 已经 unblock 哪些事

OUTPUT
Write into:
  `docs/codex-exchange/outbox/2026-05-28-1130-phase3-dto-and-status-extension.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionStatus.java`
- `backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/JudgeResult.java`
- `backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java` (adapt mock to new signature)
- `backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java` (publish with limits)
- `backend/problem-service/src/main/java/com/aioj/next/problem/domain/ProblemCatalog.java` (if needed for new method)
- `packages/api-client/src/index.ts` (type union)
- `packages/i18n/src/messages.ts` (zh/en submissionStatus)
- Any `.vue` / `.ts` 文件 with `statusTone` / status mapping (grep first)
- This inbox (Status only)
- The outbox (new)

**Read for reference**:
- `backend/problem-service/src/main/java/com/aioj/next/problem/persistence/entity/ProblemEntity.java`（确认 getter 名字）
- `backend/judge-worker/src/main/resources/application.yml`（RabbitMQ converter 配置）
- `backend/problem-service/src/main/resources/application.yml`（同上 producer 端）
- `outbox/2026-05-26-1900-sandbox-minimal-smoke-test.md` Section 12（实测字段映射依据）

**Hard 禁区**:
- `backend/api-contract/src/main/resources/db/migration/V*.sql`（不加 V7）
- `backend/**/entity/SubmissionEntity.java`（DB 字段 Phase 4 加）
- `backend/**/entity/JudgeAuditLogEntity.java`（同上）
- `TestcasePackageCache.java` / `SandboxClient.java` 真实 HTTP 逻辑（Phase 2 / 4）
- 任何 deploy/ 文件
- 新增 maven / npm 依赖

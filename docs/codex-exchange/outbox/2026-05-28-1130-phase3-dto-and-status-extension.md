# Task: Phase 3 — DTO + Status enum 扩展
## Status: done
## Created by: Codex @ 2026-05-28T11:30+08:00
## Linked: inbox/2026-05-28-1130-phase3-dto-and-status-extension.md

---

## Report

### 1. git status --short

本任务新增/修改：

```text
 M apps/web-user/src/components/common/StatusChip.vue
 M apps/web-user/src/views/HomeView.vue
 M apps/web-user/src/views/SubmissionsView.vue
 M backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java
 M backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionStatus.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/consumer/JudgeTaskListener.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/domain/JudgeResult.java
 M backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/domain/ProblemCatalog.java
 M backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java
 M packages/api-client/src/index.ts
 M packages/i18n/src/messages.ts
 M docs/codex-exchange/inbox/2026-05-28-1130-phase3-dto-and-status-extension.md
?? docs/codex-exchange/outbox/2026-05-28-1130-phase3-dto-and-status-extension.md
```

本任务还修改了一个既有未跟踪 baseline 文件：

```text
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
```

保留的既有 baseline dirty files（未回滚、未改动其业务范围）包括：

```text
 M CLAUDE.md
 M apps/web-admin/package.json
 M apps/web-admin/src/components/ProblemEditorDrawer.vue
 M apps/web-admin/src/components/TestcasePackageUploader.vue
 M apps/web-admin/src/styles.css
 M apps/web-admin/src/views/AiDraftsView.vue
 M apps/web-admin/src/views/DashboardView.vue
 M apps/web-admin/src/views/ProblemsView.vue
 M apps/web-admin/src/views/RegisterView.vue
 M apps/web-admin/src/views/RolesView.vue
 M apps/web-admin/src/views/UsersView.vue
 M apps/web-user/src/components/problem/ProblemPane.vue
 M apps/web-user/src/components/problem/ProblemTabs.vue
 M apps/web-user/src/main.ts
 M apps/web-user/src/styles.css
 M apps/web-user/src/types/problem-workspace.ts
 M apps/web-user/src/views/ProblemDetailView.vue
 M deploy/compose.yml
 M docs/HANDOVER.md
 M docs/deployment.md
 M package-lock.json
?? apps/web-admin/src/components/TestcaseManifestEditor.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? deploy/sandbox/Dockerfile
?? docs/codex-exchange/README.md
?? docs/codex-exchange/inbox/.gitkeep
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/inbox/2026-05-25-*.md
?? docs/codex-exchange/inbox/2026-05-26-*.md
?? docs/codex-exchange/outbox/2026-05-25-*.md
?? docs/codex-exchange/outbox/2026-05-26-*.md
```

没有修改 `V*.sql`、`SubmissionEntity`、`JudgeAuditLogEntity`、deploy 真实逻辑或新增依赖。

### 2. Focused diffs

#### SubmissionStatus.java

```diff
 public enum SubmissionStatus {
     QUEUED,
     RUNNING,
     ACCEPTED,
     WRONG_ANSWER,
     COMPILE_ERROR,
     RUNTIME_ERROR,
     TIME_LIMIT_EXCEEDED,
+    MEMORY_LIMIT_EXCEEDED,
+    OUTPUT_LIMIT_EXCEEDED,
     SYSTEM_ERROR
 }
```

#### JudgeTaskMessage.java

```diff
-public record JudgeTaskMessage(Long submissionId, Long problemId, Long userId, String language, String traceId) {
+public record JudgeTaskMessage(
+        Long submissionId,
+        Long problemId,
+        Long userId,
+        String language,
+        String traceId,
+        Integer timeLimitMillis,
+        Long memoryLimitKb
+) {
 }
```

#### JudgeResult.java

```diff
-public record JudgeResult(SubmissionStatus status, String message, Long timeMillis, Long memoryKb, Instant judgedAt) {
+public record JudgeResult(
+        SubmissionStatus status,
+        String message,
+        Long timeMillis,
+        Long memoryKb,
+        Instant judgedAt,
+        String stdout,
+        String stderr,
+        Integer exitStatus,
+        Long runTimeMillis
+) {
+    public static JudgeResult systemError(String message) {
+        return new JudgeResult(SubmissionStatus.SYSTEM_ERROR, message,
+                0L, 0L, Instant.now(), null, null, null, null);
+    }
 }
```

#### SandboxClient.java

```diff
     public JudgeResult judge(JudgeTaskMessage task) {
         if (!properties.getLanguageWhitelist().contains(task.language())) {
-            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled", 0L, 0L, Instant.now());
+            return new JudgeResult(SubmissionStatus.COMPILE_ERROR, "Language is not enabled",
+                    0L, 0L, Instant.now(), null, null, null, null);
         }
-        return new JudgeResult(SubmissionStatus.ACCEPTED, "Mock sandbox accepted the submission", 12L, 1024L, Instant.now());
+        return new JudgeResult(SubmissionStatus.ACCEPTED, "Mock sandbox accepted the submission",
+                12L, 1024L, Instant.now(), null, null, null, null);
     }
```

#### JudgeTaskListener.java

```diff
-import com.aioj.next.contract.submission.SubmissionStatus;
 import com.aioj.next.judge.config.JudgeQueueConfig;
 import com.aioj.next.judge.domain.JudgeResult;
@@
-import java.time.Instant;
-
@@
                 String messageText = "Testcase package unavailable: " + ex.getMessage();
                 log.warn("submission={} problem={} {}", task.submissionId(), task.problemId(), messageText);
-                judgingService.finish(task, new JudgeResult(SubmissionStatus.SYSTEM_ERROR, messageText,
-                        null, null, Instant.now()));
+                judgingService.finish(task, JudgeResult.systemError(messageText));
```

#### ProblemCatalog.java + SubmissionService.java

```diff
+import java.util.Optional;
@@
     public boolean existsActive(Long id) {
         return problemMapper.selectCount(baseProblemQuery().eq(ProblemEntity::getId, id)) > 0;
     }
+
+    public Optional<ProblemEntity> findActive(Long id) {
+        return Optional.ofNullable(problemMapper.selectOne(baseProblemQuery().eq(ProblemEntity::getId, id)));
+    }
@@
-        ProblemEntity problem = problemMapper.selectOne(baseProblemQuery().eq(ProblemEntity::getId, id));
-        if (problem == null) {
-            throw new DomainException(ErrorCode.NOT_FOUND, "Problem not found");
-        }
-        return problem;
+        return findActive(id)
+                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Problem not found"));
```

```diff
+import com.aioj.next.problem.persistence.entity.ProblemEntity;
@@
-        if (!problemCatalog.existsActive(request.problemId())) {
-            throw new DomainException(ErrorCode.NOT_FOUND, "Problem not found");
-        }
+        ProblemEntity problem = problemCatalog.findActive(request.problemId())
+                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Problem not found"));
@@
-        publishAfterCommit(new JudgeTaskMessage(submission.getId(), request.problemId(), userId, language, TraceIds.current()));
+        Long memoryLimitKb = problem.getMemoryLimitKb() == null ? null : problem.getMemoryLimitKb().longValue();
+        publishAfterCommit(new JudgeTaskMessage(submission.getId(), request.problemId(), userId, language,
+                TraceIds.current(), problem.getTimeLimitMillis(), memoryLimitKb));
```

#### packages/api-client/src/index.ts

```diff
   | 'COMPILE_ERROR'
   | 'RUNTIME_ERROR'
   | 'TIME_LIMIT_EXCEEDED'
+  | 'MEMORY_LIMIT_EXCEEDED'
+  | 'OUTPUT_LIMIT_EXCEEDED'
   | 'SYSTEM_ERROR';
```

#### packages/i18n/src/messages.ts

```diff
       COMPILE_ERROR: '编译错误',
       RUNTIME_ERROR: '运行错误',
       TIME_LIMIT_EXCEEDED: '超时',
+      MEMORY_LIMIT_EXCEEDED: '内存超限',
+      OUTPUT_LIMIT_EXCEEDED: '输出超限',
       SYSTEM_ERROR: '系统错误'
@@
       COMPILE_ERROR: 'Compile Error',
       RUNTIME_ERROR: 'Runtime Error',
       TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
+      MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
+      OUTPUT_LIMIT_EXCEEDED: 'Output Limit Exceeded',
       SYSTEM_ERROR: 'System Error'
```

#### StatusChip / status tone mapping

```diff
 // SubmissionsView.vue
   'TIME_LIMIT_EXCEEDED',
+  'MEMORY_LIMIT_EXCEEDED',
+  'OUTPUT_LIMIT_EXCEEDED',
   'SYSTEM_ERROR'
@@
   if (status === 'COMPILE_ERROR') return 'warning';
+  if (status === 'MEMORY_LIMIT_EXCEEDED') return 'warning';
+  if (status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
```

```diff
 // ProblemSubmissionsPanel.vue
   if (status === 'COMPILE_ERROR') return 'warning';
+  if (status === 'MEMORY_LIMIT_EXCEEDED') return 'warning';
+  if (status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
```

```diff
 // HomeView.vue
   if (status === 'ACCEPTED') return 'success';
   if (status === 'QUEUED' || status === 'RUNNING') return 'primary';
+  if (status === 'MEMORY_LIMIT_EXCEEDED' || status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
   return 'danger';
```

```diff
 // StatusChip.vue
-  if (props.status === 'WRONG_ANSWER' || props.status === 'TIME_LIMIT_EXCEEDED') return 'warning';
+  if (props.status === 'WRONG_ANSWER'
+      || props.status === 'TIME_LIMIT_EXCEEDED'
+      || props.status === 'MEMORY_LIMIT_EXCEEDED'
+      || props.status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
```

### 3. Compile / typecheck outputs

仓库根目录没有 `pom.xml`，所以后端命令按同一模块范围改用 `-f backend/pom.xml` 执行。

```text
$ mvn -f backend/pom.xml -pl api-contract -am compile -q
(no output; command exited 0)
```

```text
$ mvn -f backend/pom.xml -pl problem-service -am compile -q
(no output; command exited 0)
```

```text
$ mvn -f backend/pom.xml -pl judge-worker -am compile -q
(no output; command exited 0)
```

```text
$ npm run typecheck -w @aioj/web-user

> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
```

```text
$ npm run typecheck -w @aioj/web-admin

> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
```

### 4. StatusChip audit

| 文件 | 命中点 | 处理 |
|---|---|---|
| `apps/web-user/src/views/SubmissionsView.vue` | `statuses` filter + `statusTone` | 增加 MLE/OLE filter options，并映射到 `warning` |
| `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue` | `statusTone` | 增加 MLE/OLE 到 `warning` |
| `apps/web-user/src/views/HomeView.vue` | `latestStatusTone` | 增加 MLE/OLE 到 `warning` |
| `apps/web-user/src/components/common/StatusChip.vue` | fallback `status` prop tone map | 增加 MLE/OLE 到 `warning` |
| `apps/web-user/src/components/submission/SubmissionDetailModal.vue` | 只读取 `submissionStatus.*` 文案 | 无 tone map；i18n 已覆盖新状态 |
| `apps/web-user/src/components/problem/CodeSubmitPanel.vue` | 只读取 `submissionStatus.*` 文案 | 无 tone map；i18n 已覆盖新状态 |
| `apps/web-admin` | 未发现 submissions status tone map | 无需修改 |

### 5. RabbitMQ compatibility check

- Producer 端 `backend/problem-service/.../JudgeQueueConfig.java` 显式注册 `Jackson2JsonMessageConverter`，并把该 converter 设置到 `RabbitTemplate`。
- Consumer 端 `backend/judge-worker/.../JudgeQueueConfig.java` 同样注册 `Jackson2JsonMessageConverter`，`@RabbitListener` 直接接收 `JudgeTaskMessage`。
- 本次给 `JudgeTaskMessage` 追加两个 nullable record component。新生产者和新消费者之间正常；旧 5 字段消息在 Jackson record 反序列化时通常会把缺失 nullable 字段作为 `null` 处理，但不同 Jackson/Spring AMQP 配置对 missing creator arguments 的容忍度可能不完全一致。
- 安全升级建议：如果 `aioj.judge.queue` 中有积压旧消息，部署前手动 purge queue，避免新 worker 消费旧 JSON 时出现反序列化失败；本 Phase 未修改 RabbitMQ 配置。

### 6. Chinese summary

本 Phase 扩展了 2 个终态枚举、1 个 judge task DTO 和 1 个 judge result DTO，让 Phase 4 能承载 MLE/OLE、stdout/stderr、exitStatus 和 runTimeMillis。前端同步了 4 处状态色彩映射，MLE/OLE 统一使用 warning tone，因为它们是资源或输出限制类的用户代码问题，不适合和系统错误同色。problem-service 采用 A 方案：在 `ProblemCatalog` 暴露 `findActive`，提交时直接拿 active problem 的 time/memory limit 快照并发布到 judge task，避免 worker 跨机再查 problems 表。RabbitMQ 当前两端都是 Jackson JSON converter，新消息兼容；但若队列中已有旧 5 字段消息，建议升级前清队列以规避 record missing 参数风险。Phase 4 现在可以继续实现真实 SandboxClient 状态映射、单位换算、输出截断和审计字段落库，而不需要再改前端基础枚举与展示层。

## Next-action hint

- Phase 4 写真实 go-judge 映射时，需要把 ns/byte 用 ceiling division 转成 ms/KB，并决定 stdout/stderr/runTimeMillis 写入哪个新 DB 字段。

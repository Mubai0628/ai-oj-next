# Task: 代码沙箱模块设计调研（不改代码，只输出研究报告）
## Status: done
## Created by: Codex @ 2026-05-26T21:20+08:00
## Linked: inbox/2026-05-26-1530-sandbox-design-research.md

---

## Report

### Section 1: 历史设计期望汇总

#### 接口契约

- **硬约束**：评测代码必须在隔离沙箱里跑，judge-worker 永远通过 `SANDBOX_ENDPOINT` 调外部沙箱，不允许在 worker 进程内直接执行用户代码。来源：`CLAUDE.md:11`、`CLAUDE.md:126-128`、`docs/DEVELOPMENT.md:11-12`。
- **硬约束**：problem-service 触发判题只能发 RabbitMQ `JudgeTaskMessage`，禁止 HTTP 直连 judge-worker。来源：`CLAUDE.md:86`、`CLAUDE.md:207`、`docs/HANDOVER.md:82`。
- **明确现状**：`JudgeWorkerProperties` 已经给出默认 HTTP endpoint `http://localhost:8090/execute`、`SANDBOX_TOKEN`、`SANDBOX_TIMEOUT` 和语言白名单 `java,cpp,python`。来源：`backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java:8-14`、`backend/judge-worker/src/main/resources/application.yml:40-45`。
- **暗示但未明确**：当前 `JudgeTaskMessage` 只有 `submissionId/problemId/userId/language/traceId`，不包含源码、时间限制、内存限制、测试用例包路径；因此真实 HTTP contract 需要由 judge-worker 在调用沙箱前装配更多上下文。来源：`backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java:1-4`、`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SandboxClient.java:18-22`。

#### 安全

- **硬约束**：真实隔离沙箱是 P0，未实现内容包括 cgroup、namespace、seccomp、语言运行时限制、输出限制、资源审计。来源：`docs/HANDOVER.md:69`、`docs/ROADMAP.md:13`。
- **硬约束**：沙箱凭证必须用 `SANDBOX_TOKEN`，从 Docker secrets 注入，不写进代码或 `.env`。来源：`CLAUDE.md:127-128`、`docs/deployment.md:28-29`。
- **暗示但未明确**：测试包 zip 解压已有路径穿越防护，沙箱执行阶段也应该延续 “路径必须在受控 root 内” 的原则。来源：`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java:129-132`、`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java:203-219`、`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/TestcasePackageCache.java:247-250`。

#### 资源限制

- **硬约束**：题目表已有 `time_limit_millis` 和 `memory_limit_kb`，提交结果表已有 `time_millis` 和 `memory_kb`，说明沙箱必须返回至少时间和内存用量。来源：`backend/api-contract/src/main/resources/db/migration/V1__baseline.sql:27-34`、`backend/api-contract/src/main/resources/db/migration/V1__baseline.sql:55-65`。
- **明确现状**：当前 `JudgeResult` 只承载 `status/message/timeMillis/memoryKb/judgedAt`，没有 stdout/stderr、exitCode、signal、stackKb、per-case 结果。来源：`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/JudgeResult.java:1-8`。
- **暗示但未明确**：`ROADMAP` 写了输出截断和资源审计，但没有定义 stdout/stderr 截断大小、per-case 超限策略、堆栈限制是否必须单独记录。来源：`docs/ROADMAP.md:13`。

#### 可观测

- **明确现状**：judge-worker 有 `judge_audit_logs`，记录 `submission_id/from_status/to_status/worker_id/message/created_at`。来源：`backend/api-contract/src/main/resources/db/migration/V2__production_foundation.sql:25-35`、`backend/judge-worker/src/main/java/com/aioj/next/judge/persistence/entity/JudgeAuditLogEntity.java:10-19`。
- **明确现状**：状态迁移会写审计日志，消息最大 512 字符，审计失败只 warn 不阻塞判题。来源：`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java:25`、`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java:113-132`。
- **暗示但未明确**：HANDOVER 已有 “资源审计” 风险声明，但没有落到沙箱级 traceId、容器 ID、syscall trace、沙箱 stderr 等字段。来源：`docs/HANDOVER.md:69`、`backend/api-contract/src/main/java/com/aioj/next/contract/judge/JudgeTaskMessage.java:3`。

#### 部署

- **明确现状**：部署文档已有 `compose --profile app --profile judge`、judge-worker 端口 8203、RabbitMQ 管理端口 15672，服务应在内部网络内。来源：`docs/deployment.md:8-17`、`docs/deployment.md:21-31`。
- **硬约束**：judge-worker 可横向扩 N，按 RabbitMQ 队列深度独立扩容。来源：`CLAUDE.md:45-46`、`CLAUDE.md:75-81`、`docs/deployment.md:30-31`。
- **暗示但未明确**：`Sandbox HTTP（隔离主机）` 出现在架构图里，但没有说明 sandbox 与 judge-worker 是同机、同 compose、单独主机还是跨网络。来源：`CLAUDE.md:75-81`。

#### 多语言

- **明确现状**：提交入口和 worker 均限制 `java/cpp/python`；problem-service 在提交时校验，worker 在消费时再次校验。来源：`backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java:31-35`、`backend/problem-service/src/main/java/com/aioj/next/problem/domain/SubmissionService.java:46-71`、`backend/judge-worker/src/main/java/com/aioj/next/judge/domain/SubmissionJudgingService.java:104-110`、`backend/judge-worker/src/main/java/com/aioj/next/judge/config/JudgeWorkerProperties.java:13`。
- **暗示但未明确**：C++ 需要编译阶段、Java 可能需要编译和 classpath、Python 通常直接解释运行；当前 contract 没有区分 compile/run 两阶段。来源：`backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionCreateRequest.java:6`、`backend/api-contract/src/main/java/com/aioj/next/contract/submission/SubmissionStatus.java:3-11`。

补充：`backend/judge-worker/README.md` 不存在；`docs/codex-prompts/README.md` 引用了 `003-real-sandbox-skeleton.txt`，但当前目录只有 README，没有历史 `.txt` prompt 可读。来源：`docs/codex-prompts/README.md:20`，实际目录 listing。

### Section 2: judge-worker 现状骨架 vs 缺口

| 模块 | 已实现 | 缺口（未实现 / Mock） | 关键文件 |
|---|---|---|---|
| 应用启动 | Spring Boot + Rabbit + MyBatis mapper 扫描 + properties 绑定。来源：`JudgeWorkerApplication.java:11-18` | 无健康检查 endpoint；未暴露 worker readiness 与 sandbox readiness。 | `backend/judge-worker/src/main/java/com/aioj/next/judge/JudgeWorkerApplication.java` |
| RabbitMQ 消费 | `@RabbitListener` 监听 `aioj.judge.queue`，手动 ack/nack；重复或非 QUEUED 任务会 ack。来源：`JudgeTaskListener.java:36-43` | catch-all 异常直接 `basicNack(..., requeue=false)` 进 DLQ，没有按可重试异常重回队列的策略。来源：`JudgeTaskListener.java:63-72` | `JudgeTaskListener.java`、`JudgeQueueConfig.java:15-53` |
| 状态机 | `QUEUED -> RUNNING` 使用条件 update，终态只允许从 `RUNNING` 更新；脏任务抛 `NonRetryableJudgeTaskException`。来源：`SubmissionJudgingService.java:40-63`、`SubmissionJudgingService.java:65-84` | 终态枚举没有 `MEMORY_LIMIT_EXCEEDED`、`OUTPUT_LIMIT_EXCEEDED`；没有 per-case 状态。来源：`SubmissionStatus.java:3-11` | `SubmissionJudgingService.java`、`SubmissionStatus.java` |
| 沙箱客户端 | 只有语言白名单检查；否则固定返回 ACCEPTED、12ms、1024KB。来源：`SandboxClient.java:18-22` | 无真实 HTTP 调用、无 Authorization token、无 timeout、无健康检查、无重试/熔断、无 stdout/stderr/exit code/signal。 | `SandboxClient.java`、`JudgeWorkerProperties.java:10-13` |
| 测试用例包缓存 | 能查 READY + active 包，校验本地 zip 文件大小和 sha256，安全解压到 worker cache，生成 `PreparedTestcasePackage` 和 case 列表。来源：`TestcasePackageCache.java:54-71`、`TestcasePackageCache.java:83-99`、`TestcasePackageCache.java:124-168` | `JudgeTaskListener` 只 prepare 并 log 包信息，没有把 `PreparedTestcasePackage` 传入沙箱执行，也没有逐 case loop/comparator。来源：`JudgeTaskListener.java:45-58` | `TestcasePackageCache.java`、`PreparedTestcasePackage.java`、`PreparedTestcaseCase.java` |
| 输出比较 | 定义了 `OutputComparator` 接口和 `ComparisonResult`。来源：`OutputComparator.java:1-10` | 没有实现类；`SandboxClient` 不产生 stdout 文件；`TestcaseRunContext` 未被使用。来源：`TestcaseRunContext.java:1-11`、`SandboxClient.java:18-22` | `OutputComparator.java`、`TestcaseRunContext.java` |
| 审计日志 | `judge_audit_logs` 表和 entity/mapper 已有；状态迁移时写 workerId、message。来源：`V2__production_foundation.sql:25-35`、`JudgeAuditLogEntity.java:10-19`、`SubmissionJudgingService.java:113-122` | 审计字段不足以记录 sandbox endpoint、sandbox run id、exit code、signal、stderr 摘要、资源峰值、重试次数。 | `JudgeAuditLogEntity.java`、`SubmissionJudgingService.java` |
| 异常分类 | `NonRetryableJudgeTaskException` 用于脏任务，语言/标识缺失直接 DLQ。来源：`NonRetryableJudgeTaskException.java:1-7`、`SubmissionJudgingService.java:104-110` | 可重试 vs 不可重试只在文档写了原则；代码 catch-all 直接 DLQ，没有网络超时重试、sandbox 5xx 重试、熔断等策略。来源：`CLAUDE.md:129`、`JudgeTaskListener.java:68-72` | `JudgeTaskListener.java`、`SubmissionJudgingService.java` |
| 数据表 | `submissions` 存源码、状态、judge message、time/memory、retry；`problems` 存 time/memory limit；`testcase_packages/cases` 存 manifest 派生 case 路径。来源：`V1__baseline.sql:27-34`、`V1__baseline.sql:55-65`、`V4__testcase_package_storage.sql:1-40` | worker 当前没有 Problem mapper/ProblemEntity，无法读取题目限额；`JudgeTaskMessage` 也没带限额。 | `V1__baseline.sql`、`V4__testcase_package_storage.sql`、`JudgeTaskMessage.java` |

### Section 3: Sandbox HTTP API Contract

#### 现有线索

- endpoint 默认为 `http://localhost:8090/execute`，因此历史意图是独立 HTTP 沙箱服务。来源：`JudgeWorkerProperties.java:10`、`application.yml:40-43`。
- 鉴权 token 由 `SANDBOX_TOKEN` 配置，但当前代码还未使用。来源：`JudgeWorkerProperties.java:11`、`CLAUDE.md:128`。
- 当前 worker 能拿到 task 中的 `submissionId/problemId/userId/language/traceId`，但源码、题目限额、测试包 case 文件路径需要 worker 额外查询/装配。来源：`JudgeTaskMessage.java:3`、`SubmissionEntity.java:16-21`、`PreparedTestcaseCase.java:5-13`。

#### 建议 endpoint

| 项 | 建议 |
|---|---|
| Method | `POST` |
| Path | `/execute` |
| Headers | `Authorization: Bearer ${SANDBOX_TOKEN}`、`Content-Type: application/json`、`X-Trace-Id: {traceId}` |
| HTTP 语义 | 2xx 表示沙箱完成一次执行，哪怕结果是 WA/TLE/MLE/RE/CE；4xx 表示请求 contract 错误；5xx 表示沙箱服务故障，worker 可按策略重试/熔断。 |

#### 请求 JSON 建议

```json
{
  "traceId": "string",
  "submissionId": 123,
  "problemId": 456,
  "language": "cpp",
  "sourceCode": "...",
  "compile": {
    "enabled": true,
    "args": ["-O2", "-std=c++17"]
  },
  "run": {
    "stdin": "...",
    "args": [],
    "timeLimitMillis": 1000,
    "memoryLimitKb": 262144,
    "stackLimitKb": 65536,
    "outputLimitKb": 1024
  },
  "case": {
    "packageId": 1,
    "caseId": 10,
    "sample": false
  },
  "debug": {
    "captureSyscallTrace": false
  }
}
```

字段依据：`language/sourceCode` 来自 submissions 表和 DTO；`timeLimitMillis/memoryLimitKb` 来自 problems 表；`caseId/packageId/stdin` 来自测试包缓存。来源：`SubmissionCreateRequest.java:6`、`V1__baseline.sql:27-34`、`V1__baseline.sql:55-65`、`PreparedTestcaseCase.java:5-13`。

#### 响应 JSON 建议

```json
{
  "status": "ACCEPTED",
  "timeMillis": 12,
  "memoryKb": 1024,
  "stackKb": 128,
  "stdout": "string, truncated",
  "stderr": "string, truncated",
  "compileOutput": "string, truncated",
  "exitCode": 0,
  "signal": null,
  "message": "Accepted",
  "syscallTrace": null,
  "sandboxRunId": "string"
}
```

状态建议采用沙箱内部细粒度值：`ACCEPTED / WRONG_ANSWER / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED / RUNTIME_ERROR / COMPILE_ERROR / SYSTEM_ERROR / OUTPUT_LIMIT_EXCEEDED`。注意当前 `SubmissionStatus` 还没有 `MEMORY_LIMIT_EXCEEDED` 和 `OUTPUT_LIMIT_EXCEEDED`，实现时要么先映射到 `RUNTIME_ERROR/SYSTEM_ERROR`，要么扩展 contract。来源：`SubmissionStatus.java:3-11`、`JudgeResult.java:7`。

#### 执行形态建议

- 第一阶段可以让 sandbox API 一次只跑一个 case，worker 负责遍历 case、比较输出、聚合最终状态；这最贴合当前 `PreparedTestcaseCase` 和未来 `OutputComparator`。来源：`PreparedTestcaseCase.java:5-13`、`OutputComparator.java:6-10`。
- 如果后续要提高吞吐，可以扩展成一次请求包含多个 cases，但要先定义 per-case response 和部分失败策略；当前数据库没有 per-case run 表。来源：`V1__baseline.sql:55-65`、`V2__production_foundation.sql:25-35`。

### Section 4: 业界方案对比表

| 方案 | 一句话原理 | 安全等级 | Windows host 开发难度 | 集成进 judge-worker 的成本 | 资源信息颗粒度 | 维护成本 |
|---|---|---|---|---|---|---|
| isolate (ioi/isolate) | Linux namespaces + cgroups + setuid root sandbox，面向竞赛/OJ。来源：`https://raw.githubusercontent.com/ioi/isolate/master/README.md` L0-L2、`https://raw.githubusercontent.com/ioi/isolate/master/isolate.1.txt` L31-L35 | high（OJ 经典，但需正确 Linux/cgroup setup） | 不能 Windows native；需 Docker/WSL2/Linux VM。来源：isolate manual L31-L35 | 中等：需要自己包 HTTP 服务、编译运行脚本、语言镜像和 meta 解析 | 很好：time、wall time、max-rss、exitcode、signal、cg-mem、status 等。来源：isolate manual L26-L30 | 中等偏高：Linux 内核/cgroup/systemd 依赖明显 |
| Judge0 CE | 开源 sandbox-as-service，Web API + worker，底层使用 isolate，支持多语言。来源：`https://ce.judge0.com/docs` L209-L215、L268-L274 | high（成熟产品，但黑盒程度更高） | Docker compose 最容易；Windows 通过 Docker Desktop 跑 | 低到中：已有 HTTP API，但语言 ID、submission token、异步模型要适配 | 好：stdout/stderr/time/memory/compile_output/message/status，且支持 wait 或 token 查询。来源：`https://ce.judge0.com/docs` L719-L779 | 中等：要维护 Judge0 自身服务栈、语言版本和配置 |
| go-judge (criyle/go-judge) | Go 实现的 sandbox service，REST/gRPC，cgroup/seccomp/rlimit，单 binary 或 Docker。来源：`https://github.com/criyle/go-judge` L286-L296、L356-L374 | high（支持 dangerous syscall/seccomp，但依赖 privileged/cgroup 权限正确配置） | Windows/macOS 仅实验，不适合生产；Windows 开发建议 Docker/WSL2。来源：`https://github.com/criyle/go-judge` L391-L404 | 低到中：本身就是 REST/gRPC 服务，和 `SANDBOX_ENDPOINT` 很贴 | 很好：区分 AC/TLE/MLE/OLE/Non-zero/signalled/dangerous syscall/internal 等；内存受 cgroup 版本影响。来源：`https://github.com/criyle/go-judge` L356-L374、L394-L411 | 中等：比 Judge0 轻，但仍要理解 cgroup/root/privileged 容器 |
| 自研 Docker per-submission | 每次提交起临时容器，配 `--memory/--cpus/--network none/--read-only/cap-drop/seccomp/no-new-privileges`。来源：Docker run docs `https://docs.docker.com/engine/containers/run/`，Docker CLI run `https://docs.docker.com/reference/cli/docker/container/run/` | medium（容器不是强安全边界，依赖 Docker daemon 隔离和配置） | Docker Desktop 最直观，Windows 开发友好 | 中等：自己写容器生命周期、超时、输出截断、编译/运行、清理 | 一般：exit code、stdout/stderr 容易；精确 max RSS/stack/syscall trace 较难 | 高：安全细节和性能优化都自担 |
| gVisor | 用户态 application kernel + OCI runtime `runsc`，在容器和宿主内核之间加隔离层。来源：`https://gvisor.dev/docs/` L77-L86、L123 | high（强于普通容器，弱于完整 VM 的某些场景） | 主要面向 Linux/Docker/K8s；Windows 需 WSL2/Docker 路径 | 中到高：更像运行时增强，不直接给 OJ API | 一般：依赖外层 runner 采集资源，非 OJ 专用 | 中高：runtime、兼容性、性能调优成本 |
| Firecracker | KVM microVM，硬件虚拟化隔离，面向安全多租户 serverless/container workloads。来源：`https://github.com/firecracker-microvm/firecracker` L350-L355 | very high | 本地 Windows 开发很重；需要 Linux/KVM，通常不适合作为第一阶段 | 高：要自建 rootfs、agent、网络、文件注入、生命周期管理 | 取决于自建 agent；不是 OJ 专用 | 高：生产很强，但 MVP 太重 |

推荐：**本项目第一阶段优先评估 go-judge 或 Judge0**。如果目标是最快落地且少写沙箱内部代码，Judge0 最省心；如果希望保持 AIOJ 自己的判题状态机、测试包缓存和 HTTP contract，同时要轻量可维护，go-judge 更贴合 `SANDBOX_ENDPOINT` 模式。isolate 直集成适合长期定制，但需要自己包完整 HTTP 服务和语言运行时。

### Section 5: Windows 开发路径建议

#### A. Docker Desktop + WSL2 backend（推荐）

- 推荐原因：Docker 官方文档显示 WSL2 backend 是 Docker Desktop Windows 的默认/常规路径，支持 Linux container，并能从 Windows terminal 使用 docker 命令。来源：`https://docs.docker.com/desktop/features/wsl/` L901-L928。
- 安全注意：Docker Desktop 的 WSL2 集成遵循 WSL 共享内核安全模型；若需要更严格隔离，可切 Hyper-V 或启用 Enhanced Container Isolation。来源：`https://docs.docker.com/desktop/features/wsl/` L957-L965。
- 步骤大纲：
  1. 安装 Docker Desktop，启用 WSL2 backend。
  2. 选定 sandbox 镜像：`judge0` / `go-judge` / 包装 isolate 的自研 sandbox-service。
  3. 配置 `SANDBOX_ENDPOINT=http://localhost:{port}/execute` 和 `SANDBOX_TOKEN`。
  4. judge-worker 可先跑 host Java，sandbox 跑 Linux container；调试时看 sandbox stdout/container logs，worker 用 IDEA attach。
  5. 用 `JUDGE_LANGUAGE_WHITELIST=java,cpp,python` 对齐镜像内 runtime。

#### B. Docker Desktop + Hyper-V backend

- 优点：比 WSL2 shared-kernel 模型隔离边界更清楚，Docker 官方也建议更严格隔离场景可用 Hyper-V。来源：`https://docs.docker.com/desktop/features/wsl/` L963-L965。
- 缺点：开发体验和文件共享通常更重；对于本项目 Windows 本机迭代，不如 WSL2 顺手。
- 适用：安全演示或用户机器 WSL2 不稳定时作为 fallback。

#### C. Mock sandbox in dev + 真沙箱仅 Linux 测试环境

- 优点：本地启动最轻；当前 `SandboxClient` 已经是 mock accepted，可继续让普通业务开发不依赖 Docker。来源：`SandboxClient.java:18-22`。
- 缺点：本地测不到 CE/TLE/MLE/RE/WA、stdout/stderr、资源限制、测试包路径挂载等真实判题行为；这会掩盖 P0 风险。来源：`docs/HANDOVER.md:69`、`docs/ROADMAP.md:13`。
- 适用：保留为开关，不应作为主开发路径。

建议：采用 **A 为主，C 为 fallback**。也就是 Windows 本机通过 Docker Desktop + WSL2 跑真实 sandbox container，允许 `SANDBOX_MOCK=true` 之类的本地逃生开关，但 CI/staging 必须跑真沙箱。

### Section 6: Open questions

| 问题 | 背景 | 选项 | 我的倾向 |
|---|---|---|---|
| 选 go-judge、Judge0、isolate 直包，还是 Docker per-submission？ | 当前架构已经是 `SANDBOX_ENDPOINT` 独立 HTTP 服务，但没有具体实现。来源：`JudgeWorkerProperties.java:10-13` | Judge0 快速落地；go-judge 轻量服务；isolate 自包更可控；Docker per-submission 最直观但安全/性能自担 | MVP 倾向 go-judge；若优先“今天就能跑多语言”，选 Judge0 |
| 是否必须支持 stack KB / syscall trace？ | 用户希望详细运行信息；当前 `JudgeResult` 只有 time/memory。来源：`JudgeResult.java:7` | 必须支持则偏 go-judge/isolate 自包；非必须则 Judge0 也够用 | 第一阶段只强制 stdout/stderr/exitCode/signal/time/memory，stack/syscall trace 作为可选 |
| 编译阶段与运行阶段是否分开？ | C++/Java 需要编译，Python 通常不需要；当前 `SubmissionStatus` 有 COMPILE_ERROR。来源：`SubmissionStatus.java:3-11` | sandbox API 内部处理 compile+run；或 worker 先 compile artifact 再逐 case run | API 层明确 `compile` 对象，但第一阶段可由 sandbox 内部完成 |
| 测试包如何进入 sandbox？ | worker 已经把 zip 安全解压到 cache，但还没有传给 sandbox。来源：`TestcasePackageCache.java:54-71`、`PreparedTestcaseCase.java:5-13` | bind mount cache；API 传 stdin 内容；sandbox 自己拉取对象存储 | Windows/dev 优先 API 传 stdin 内容；生产可优化为同机 bind mount |
| worker 聚合 per-case 还是 sandbox 一次跑全包？ | 当前有 `PreparedTestcaseCase` 和 `OutputComparator`，但无 per-case DB 表。来源：`PreparedTestcaseCase.java:5-13`、`OutputComparator.java:6-10` | worker loop per case；sandbox batch all cases | 第一阶段 worker loop，逻辑透明、易审计 |
| 失败重试/熔断怎么做？ | 文档要求区分可重试 vs NonRetryable，但代码 catch-all 直接 DLQ。来源：`CLAUDE.md:129`、`JudgeTaskListener.java:63-72` | 网络/5xx 重试；4xx/脏任务 DLQ；sandbox unhealthy 熔断 | 必须先定义：5xx/timeout 可重试，contract 4xx 和数据缺失不可重试 |
| 部署边界 | 架构图写 Sandbox HTTP（隔离主机），部署文档未细化。来源：`CLAUDE.md:75-81`、`docs/deployment.md:27-31` | 同 compose；同机不同容器；独立 Linux 主机；K8s | 开发同 compose；生产至少同内网独立容器，后续可拆独立主机 |
| 是否扩展提交状态枚举？ | 当前缺 MLE/OLE，但用户希望内存、输出限制细节。来源：`SubmissionStatus.java:3-11` | 不扩，映射到 RE/SE；扩展 `MEMORY_LIMIT_EXCEEDED/OUTPUT_LIMIT_EXCEEDED` | 建议扩展，否则 UI 和审计会丢失关键信息 |

### Verification

本任务按 read-only 调研执行，未修改 source code、migration、frontend、packages、依赖，也未 stage/commit/push。当前仓库已有大量前序 baseline dirty files，且 exchange 文件本身尚未入库，所以 `git status --short` 无法呈现 inbox 为 `M` 的理想状态；本任务实际写入范围仅为本 inbox 顶部状态和对应 outbox 文件。

### 3-sentence Chinese summary

历史设计意图最关键的 3 条硬约束是：判题永远通过 RabbitMQ 进入 judge-worker、judge-worker 永远调用独立 `SANDBOX_ENDPOINT` 不在进程内执行用户代码、沙箱 token 必须走 secrets/环境注入且真实沙箱要覆盖 cgroup/namespace/seccomp/资源限制/输出限制/资源审计。综合当前代码骨架和 Windows 开发诉求，我推荐第一阶段优先评估 **go-judge** 作为轻量 HTTP/gRPC 沙箱服务，若追求最快多语言开箱即用则 Judge0 是备选。下一步建议 Claude 先和用户敲定两件事：是否必须首期支持 stack KB/syscall trace，以及测试包 stdin 是由 worker 读取后通过 API 发送还是通过同机 bind mount 暴露给 sandbox。

## Next-action hint

- 下一个实施 prompt 应先做 contract 决策，不要直接写 `SandboxClient`；否则会卡在源码/题目限额/测试包 cases 由谁装配的问题上。
- 若选 go-judge，建议先用 Docker Desktop + WSL2 跑一个本地 spike：Java/C++/Python 各跑 hello + TLE + RE 三类样例，再决定 AIOJ 的响应字段映射。

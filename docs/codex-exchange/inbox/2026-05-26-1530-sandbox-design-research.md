# Task: 代码沙箱模块设计调研（不改代码，只输出研究报告）
## Status: done
## Created by: Claude @ 2026-05-26T15:30+08:00
## Linked: outbox/2026-05-26-1530-sandbox-design-research.md

---

## Prompt

ROLE
You are Codex performing a **research-only** task designed by Claude.
**Do NOT modify any source code or migration in this task.** Read,
synthesize, and write a structured report to
`docs/codex-exchange/outbox/2026-05-26-1530-sandbox-design-research.md`
following `docs/codex-exchange/README.md` template. Update this inbox's
`## Status: new` → `## Status: done` (or `blocked`) when finished.

WHY THIS RESEARCH MATTERS
代码沙箱是项目最大的 P0 风险（CLAUDE.md §8 + HANDOVER §4 都标出）。
当前 `SandboxClient.java` 是 Mock 永远返回 ACCEPTED。用户希望：

- Windows 本机开发 + 调试方便
- Docker 支持多语言（Java / C++ / Python，与 `languageWhitelist` 一致）
- 获取详细运行信息：时间、内存、栈使用、stdout/stderr、exit code 等
- 部署符合安全规范（cgroup / namespace / seccomp / 资源限制 / 输出限制 / 不能逃逸）
- 稳定 + 易维护

Claude 不上来给闭式方案。先让你**穷尽扒一遍历史 + 业界**，让 Claude
基于真实信息再下设计。**这一轮不写代码，只调研写报告。**

CONSTRAINTS (hard)
- **Read-only on backend / frontend code**. 允许新建/写：
  - 本 inbox 的 Status 字段
  - 对应 outbox 文件
- 不动 source code、配置、migration、依赖
- 不 commit / stage / push
- 报告必须**有出处**：每个论断标注来自哪个文件 / 行号 / 业界资料 URL

============================================================
TASK 1 — 通读历史设计意图
============================================================

Read 以下文件并抽取关于"沙箱"的所有约定、设计期望、风险声明：

- `CLAUDE.md` 全文（特别是 §2 技术栈、§4 架构、§5.7 沙箱、§7 禁忌、§8 God Nodes）
- `docs/HANDOVER.md` 全文（§4 待办、§5 风险、§6 关键文件锚点）
- `docs/ROADMAP.md` 全文（沙箱阶段化目标）
- `docs/DEVELOPMENT.md` 全文（如果存在，本地环境/部署细节）
- `docs/deployment.md` 全文（部署相关）
- `docs/codex-prompts/README.md` + 该目录下所有历史 `.txt` Prompt（如有提及沙箱）
- `backend/judge-worker/README.md`（如有）

输出 **Section 1: "历史设计期望汇总"**：
- 一份按主题（接口契约 / 安全 / 资源限制 / 可观测 / 部署 / 多语言）的
  bullet 列表
- 每条标注来自 `{文件}:{行号}` 或 `{文件}:{段落标题}`
- 标注哪些是**明确写死的硬约束**（如 "永远调 SANDBOX_ENDPOINT 不在
  worker 进程内执行"）vs **暗示但未明确的部分**（如 "异常区分可
  重试 vs NonRetryable" 但没说具体策略）

============================================================
TASK 2 — 当前代码骨架现状
============================================================

Read 并总结现状：

- `backend/judge-worker/src/main/java/com/aioj/next/judge/`
  全树（consumer / domain / persistence / config / Application）
- `backend/api-contract/src/main/java/com/aioj/next/contract/judge/`
  全部 DTO
- `backend/api-contract/src/main/resources/db/migration/V*.sql` 中
  与 submission / judge_audit_log / testcase_package 相关的表

输出 **Section 2: "judge-worker 现状骨架 vs 缺口"** 表：

| 模块 | 已实现 | 缺口（未实现 / Mock）| 关键文件 |
|---|---|---|---|
| RabbitMQ 消费 | ... | ... | JudgeTaskListener.java |
| 状态机 (QUEUED→RUNNING→终态) | ... | ... | SubmissionJudgingService.java |
| 沙箱客户端 | Mock | 真实 HTTP 调用、健康检查、重试 | SandboxClient.java |
| 测试用例包缓存 | ? | ? | TestcasePackageCache.java |
| 审计日志 | ? | ? | JudgeAuditLog* |
| 异常分类 | NonRetryableJudgeTaskException | 重试策略具体行为 | ... |

============================================================
TASK 3 — Sandbox HTTP API contract 推断 / 提议
============================================================

`JudgeWorkerProperties.sandboxEndpoint = "http://localhost:8090/execute"`
说明历史设计意图是把沙箱做成**独立 HTTP 服务**。

读 `SandboxClient.java` 当前签名（输入 `JudgeTaskMessage`，输出
`JudgeResult`），推断 HTTP contract 该长什么样。如果代码里有线索
（注释 / 配置 / 历史 prompt 提及），优先用线索；否则 propose 一个
合理 contract。

输出 **Section 3: "Sandbox HTTP API Contract"** —— 包含：

- 请求 endpoint / method
- 请求 body JSON 字段（语言、源码、stdin、时间限制 ms、内存限制 KB、
  output 限制 KB、可选 args、是否记录 syscall trace 等）
- 响应 JSON 字段（status [ACCEPTED/WA/TLE/MLE/RE/CE/SE]、time ms、
  memory KB、stack KB、stdout、stderr、exit code、signal、syscall trace
  可选、错误信息等）
- 状态码：2xx 一律是"沙箱执行完成"（包括 TLE/WA），5xx 才是沙箱
  本身故障
- 鉴权：`Authorization: Bearer ${SANDBOX_TOKEN}` 头

不要写 OpenAPI yaml（过度工程）；用 Markdown 表 / JSON 示例即可。

============================================================
TASK 4 — 业界沙箱方案对比（重点：哪种适合本项目 + Windows 开发）
============================================================

简短调研以下方案并对比：

1. **isolate** (ioi/isolate) — 教学/竞赛 OJ 经典选择
   - 基于 Linux cgroup + namespace，无需 Docker（也可放 Docker 里）
   - 提供时间/内存/栈/输出限制 + meta 文件
   - **Linux only**（不能在 Windows host 直接跑，但 Docker 里 ok）

2. **Judge0** — 开源 sandbox-as-service
   - Ruby + isolate，HTTP API 现成
   - Docker compose 可拉起
   - 支持多语言开箱即用

3. **go-judge** (criyle/go-judge) — Go 实现，HUSTOJ 团队主导
   - 单 binary，cgroup + seccomp + rlimit
   - HTTP/gRPC 接口
   - 性能高，多语言可扩展

4. **自研 Docker per-submission** — 每次提交起一个一次性容器
   - 简单直观，但单次启动 ~1s 慢
   - 安全性依赖 Docker daemon 隔离 + read-only / no-network / cap-drop

5. **firecracker / gVisor** — 更强隔离但更重
   - VM 级隔离
   - 适合生产，本地开发太重

每个方案给：
- 一句话原理
- 安全等级（low/med/high）
- Windows host 开发难度（必须经 Docker 还是有 native 选项）
- 集成进 judge-worker 的成本（直接 HTTP / 包一层）
- 资源信息颗粒度（能不能给到 stack KB / syscall trace）
- 维护成本

输出 **Section 4: "业界方案对比表"** + 你的 1-2 句话推荐。

============================================================
TASK 5 — Windows 本机开发的可行路径
============================================================

用户机器是 Windows。Docker Desktop for Windows 是默认选项，但下面三条
路径都可行，列优缺点：

A. **Docker Desktop + WSL2 backend**
   - 沙箱跑 Linux container，judge-worker 也跑容器或 host Java
   - 文件共享 perf 一般，但够开发用
   - 调试：sandbox container 日志走 stdout，judge-worker 用 IDEA attach

B. **Docker Desktop + Hyper-V backend**
   - 同上，但 perf 更差，现在不推荐

C. **Mock sandbox in dev + 真沙箱仅在 Linux 测试环境**
   - 本地开发不跑真沙箱，留 Mock 返回 ACCEPTED；走 CI/staging 跑真
   - 优点：本地启动快，开发流畅
   - 缺点：本地无法测真实判题行为

输出 **Section 5: "Windows 开发路径建议"** —— 推荐哪条 + 步骤大纲
（不写代码，只写阶段：a) 装 Docker Desktop b) 拉 isolate/judge0 镜像
c) 配置 SANDBOX_ENDPOINT...）。

============================================================
TASK 6 — Open questions 留给 Claude 决策
============================================================

列出**必须 Claude 与用户敲定**才能进入实施阶段的开放问题。例如：

- 选哪种沙箱方案（自研 / Judge0 / go-judge / isolate 直集成）
- 是否要求支持 syscall trace / 堆栈使用（影响沙箱选型）
- 测试包路径如何挂载到沙箱 container（NFS / 本地 bind mount / API 推送）
- 编译阶段与运行阶段是否分开（典型 C++ 要先编译再跑，沙箱 API 要支持）
- 单沙箱进程并发数 vs 每次单容器
- judge-worker 与 sandbox 的部署边界（同机 / 同 docker compose / 跨网络）
- 健康检查 + 熔断策略

每条给：背景 + 选项 + 你倾向（不强制 Claude 采纳）。

输出 **Section 6: "Open questions"**。

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  docs/codex-exchange/inbox/2026-05-26-1530-sandbox-design-research.md
     ?? docs/codex-exchange/outbox/2026-05-26-1530-sandbox-design-research.md
   **No other file modified**。本任务是 read-only 调研。如果发现需要
   改代码才能完成，停下来在 outbox 写 "blocked" 并说明。

2. 6 个 Section 全部输出到 outbox 文件。每个 Section 的论断都有出处。

3. 3-sentence Chinese summary：覆盖（a）历史设计意图最关键的 3 条
   硬约束（b）你推荐的沙箱方案（c）下一步建议 Claude 找用户敲定的
   1-2 个 open question。

OUTPUT
Write all sections into:
  `docs/codex-exchange/outbox/2026-05-26-1530-sandbox-design-research.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push, or modify any source/migration files.

## Constraints / Files in scope

**Touch (write)**:
- This inbox (Status only)
- The outbox (new file with 6 sections)

**Read for reference**:
- `CLAUDE.md`
- `docs/HANDOVER.md`
- `docs/ROADMAP.md`
- `docs/DEVELOPMENT.md` (if exists)
- `docs/deployment.md`
- `docs/codex-prompts/**`
- `backend/judge-worker/**` (read-only)
- `backend/api-contract/src/main/java/com/aioj/next/contract/judge/**`
- `backend/api-contract/src/main/resources/db/migration/V*.sql`
- WebFetch 业界沙箱方案文档（isolate / Judge0 / go-judge / 等）

**Hard 禁区**:
- backend/** 任何 .java / .xml / .yml / .sql 文件**修改**
- frontend / packages/** 任何文件
- 新增 npm / maven 依赖
- 任何 commit / stage / push

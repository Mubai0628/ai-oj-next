# AI-OJ Next — 总工程师交接手册（Chief-Engineer Handoff）

> **这份文档的定位**：你（继任的项目负责人 / 总工程师）入项目读的**第一份**文档。
> 读完之后，你应当知道：项目是什么、现在到哪了、接下来要做什么、跟谁怎么协作、
> 出问题先看哪几个文件、上手前 7 天怎么排时间。
>
> **本文档不重复**：详细约定看 `CLAUDE.md`，详细路线看 `ROADMAP.md`，详细现状看
> `HANDOVER.md`，详细架构看 `architecture.md`，详细部署看 `deployment.md`。
> 本文档是「地图」，那 5 份是「地形」。
>
> **快照时间**：2026-05-29 · **基线分支**：`master` · 最新提交以
> `git log -1 --oneline` 为准。本快照已完成 Phase 5 多语言 sandbox runtime
> 与学生提交端到端验收，详见 §6.2 与 §7.1。

---

## 0. 给你的第一封信（5 分钟版）

- **项目身份**：校园**教学型** AI 在线评测系统 v2（不是 ACM 竞赛 OJ）。差异点 ——
  AI 助教按题辅导 + AI 出题草稿需教师审核 + 配额与审计治理。
- **现在所在阶段**：Phase 0「稳固基础」的沙箱主链路已收尾。Phase 1–5
  已跑到真实 go-judge `/run`，并完成 Python / C++ / Java 多语言 runtime 与
  "学生提交 → RabbitMQ → judge-worker → sandbox → 写库 → 学生端详情展示"验收。
- **最紧迫的一件事**：进入 Phase 1（用户组）前，先补关键 service 单测与
  大测试点压测，避免在已有沙箱闭环上继续裸奔。
- **不能动的红线**（违反任意一条都会埋雷）：
  1. 判题永远经 RabbitMQ，**禁止** HTTP 直连 judge-worker
  2. AI 出题永远走 draft → approve → import，**禁止**直接调 problem-service 插题
  3. Flyway 历史脚本**只能新增**，不能改 V{N}.sql 既有内容
  4. controller 不抛裸 RuntimeException、不 try/catch 转换、不 `ResponseEntity.status()`，
     业务异常**只**抛 `DomainException(ErrorCode, msg)`
  5. 前端 16+ 位 ID **必须**当字符串，走 `preserveLargeIntegerIds()` 拦截
- **协作模式**：自 2026-05-29 起，项目默认由 **Codex 直接负责**
  需求澄清、方案设计、代码实现、验证和汇报。`docs/codex-exchange/`
  保留为历史归档，详见 §8。
- **第一周建议**：见 §12 的 7 天上手清单。

---

## 1. 项目身份与边界

| 项 | 内容 |
|---|---|
| 定位 | 校园教学型 OJ + AI 助教 + AI 出题草稿审核 |
| **不是** | ACM 竞赛 OJ、企业 LeetCode 平台、通用云 IDE |
| 核心非功能 | 评测必在隔离沙箱（`SANDBOX_ENDPOINT`），AI 全调用可审计（`AiUsageRecord`），公网暴露面=只 gateway |
| 用户群 | 学生（练习闭环）+ 教师/管理员（题库 + AI 出题 + 配额治理） + 平台管理员（用户/角色） |
| 部署目标 | Ubuntu 24 + Docker Compose；K8s 暂不实现 |

---

## 2. 技术栈速览（升级前先开会）

| 层 | 选型 | 备注 |
|---|---|---|
| JDK | 17 | **不要**降到 1.8 |
| Spring Boot / Cloud / Alibaba | 3.5.14 / 2025.0.2 / 2025.0.0.0 | |
| MyBatis-Plus | 3.5.16 | |
| JJWT | 0.12.6 | |
| SpringDoc | 2.8.17 | `/swagger-ui.html` |
| MySQL / Redis / RabbitMQ / Nacos / Sentinel | 8 / 7 / 3.x / 3.x / latest | |
| Vue / Vite / TypeScript / Pinia / Arco Vue | 3.5.34 / 8.0.13 / 5.9.3 / 3.0.4 / 2.58.0 | |
| Node 包管理 | **npm workspaces** | 不是 pnpm 不是 yarn 不是 turbo |
| Sandbox | criyle/go-judge | 容器内嵌套受限执行环境 |
| Markdown | md-editor-v3 ^5.8.5 | hoisted by workspaces |

---

## 3. 架构与服务边界

### 3.1 拓扑

```
                Browser (5173 / 5174)
                       │
                       ▼  唯一公网入口
            gateway-service :8101 (CORS + TraceId + Sentinel)
              │   │   │   │
   ┌──────────┘   │   │   └────────────┐
   ▼              ▼   ▼                ▼
 auth-svc   problem-svc   ai-svc   /api/v1/submissions
 :8201        :8202        :8204       │
                │                      │ (publish RabbitMQ, 永不 HTTP)
                └──────────────┬───────┘
                               ▼
                       judge.tasks queue
                               │
                               ▼
                     judge-worker :8203 ×N  ──HTTP──▶ sandbox :8090
                               ▲                       (go-judge,
                               │                        privileged,
                               │ HTTP /api/v1/internal  独立判题主机)
                               │ /testcase-packages/{id}/blob
                               │ + X-Internal-Token
                               └─── problem-service (内网 only)
```

### 3.2 三种部署形态（与判题机隔离）

- **All-in-one**：dev / 小型生产，所有服务一台主机
- **Backend + Judge 分离（生产推荐）**：后端主机 A（公网仅 gateway 暴露）+ 判题主机 B/C/...（独立硬件，`privileged: true` sandbox container）
- **K8s**：未来项；需要把 worker+sandbox 做成同节点单元 + node affinity 保证共享卷

详见 `docs/deployment.md §3`。

### 3.3 跨服务通信硬约束

| 通信链路 | 协议 | 凭据 | 备注 |
|---|---|---|---|
| Browser → gateway | HTTPS | JWT (access + refresh) | 唯一公网入口 |
| gateway → 业务服务 | HTTP | TraceId 透传 | `/api/v1/**` 前缀 |
| problem-service → judge-worker | **RabbitMQ** | `JudgeTaskMessage` 经 Jackson | 禁止 HTTP 直连 |
| judge-worker → problem-service | HTTP | `X-Internal-Token` | 仅 `/api/v1/internal/**`，gateway 不转发 |
| judge-worker → sandbox | HTTP | `SANDBOX_TOKEN` | 通过 `aioj-judge` Docker network |
| ai-service → problem-service | HTTP (`ProblemServiceClient`) | 内部调用 | 不跨库读 |

---

## 4. 仓库导航

```
backend/                       Maven 多模块（parent pom.xml）
  common-lib/                  ApiResponse / ErrorCode / GlobalExceptionHandler / JWT / TraceId / InternalApiTokenFilter
  api-contract/                跨服务 DTO + Flyway 迁移 resources/db/migration/V*.sql
  gateway-service/  :8101      WebFlux 网关
  auth-service/     :8201      用户、角色、登录、注册、JWT 刷新
  problem-service/  :8202      题目、提交、测试包（分片上传）、判题任务发布、内部 blob 端点
  judge-worker/     :8203      RabbitMQ 消费者，SandboxClient → go-judge /run
  ai-service/       :8204      AI 对话、出题草稿、配额、OpenAI 兼容
apps/
  web-user/         :5173      学生端 SPA
  web-admin/        :5174      教师/管理员端 SPA
packages/
  api-client/                  浏览器 API 客户端（fetch + JWT 自动刷新 + SSE + ApiError）
  ui/                          共享 Vue 组件（目前仅 OjStat / OjToolbar）
  i18n/                        国际化 messages + 语言切换
deploy/                        Docker Compose、Nginx、Dockerfile、secrets 示例
  sandbox/Dockerfile           go-judge runtime image（Phase 5 需扩多语言）
  compose.yml                  profiles: infra / app / judge
docs/                          架构、部署、运维、交接、codex-exchange
graphify-out/                  本地用 graphify update . 生成，.gitignore
```

详细约定见 `CLAUDE.md §3 Repository Layout`。

---

## 5. 红线规则（违反必中招）

### 5.1 API / 异常
- 全后端响应 `ApiResponse<T>`（`code/message/data/details/traceId/timestamp`），`code=0` 成功
- 业务错误**只**抛 `DomainException(ErrorCode, msg)`
- 校验异常由 `common-lib/GlobalExceptionHandler` 折叠成 `details: Map<field, msg>`，controller 不要再处理
- 兜底 handler **绝不**回传 `ex.getMessage()`，只回 `INTERNAL_ERROR` + 把堆栈带 traceId 落服务端日志
- 前端用 `instanceof ApiError` 区分；字段错误绑 `<a-form-item :validate-status :help>`；通用错误用 `ApiError.userMessage`（走 i18n `errors.<code>`）
- 新增错误码：先 `ErrorCode.java` 加常量 → 再 `packages/i18n/src/messages.ts` 的 `errors.*` 双语同步

### 5.2 路由前缀
公开 API **必须**在 `/api/v1/**`；内部调用 **必须**在 `/api/v1/internal/**`，gateway 不转发。

### 5.3 ID 与鉴权
- 后端 ID = Long（雪花），前端 16+ 位字段 **必须**当 string，复用 `preserveLargeIntegerIds()`
- 双 token：access 2h / refresh 14d；前端 401 自动 refresh 一次再失败抛 `aioj:auth-expired` 全局事件（管理端监听挂在 router 顶层）
- 服务端校验由 `BearerTokenAuthenticationFilter` 统一做，controller 不要手写

### 5.4 数据库
- 字段 snake_case，**表名复数**（`users` / `problems` / `submissions` / `problem_test_cases`）
- 实体显式 `@TableName("...")`
- Flyway 历史**不可改**；只新增 V{N+1}__*.sql
- 多实例部署：只允许 1 个实例 `FLYWAY_ENABLED=true` 跑迁移，其他后启

### 5.5 AI
- 出题：draft → admin approve → import 三步；禁止直接调 problem-service 插题
- 对话：必须先写 `AiUsageRecord` 和扣 `AiQuotaPolicy`，超额返回特定 code
- Provider：实现 `AiProvider` 接口；新增 provider 不改 controller
- 流式：SSE `/api/v1/ai/chat/stream`，事件名 `meta / message / done / error`

### 5.6 沙箱
- judge-worker 永远调 `SANDBOX_ENDPOINT`，不在自己进程内执行用户代码
- `SANDBOX_TOKEN` 从 Docker secrets 注入，**不写代码或 .env**
- 异常分类：可重试 vs `NonRetryableJudgeTaskException`（脏数据直接死信）

### 5.7 前端
- `views/` 不直接 fetch，统一过 `@aioj/api-client.api`
- 共享 UI 下沉到 `@aioj/ui`，**不**在 user/admin 各自重写
- `<a-alert>` **必须**用 default slot：`<a-alert>{{ msg }}</a-alert>`，**禁用** self-closing + `:content` 写法（Arco 2.58 + Vue 3.5 渲染异常）
- 响应式 **必选**：桌面 ≥1280 / 平板 768-1280 / 移动 <768 三档；用 `grid auto-fit + minmax`、`clamp()`；drawer/modal 在 mobile ≥90% 视口宽
- i18n 文案放 `@aioj/i18n/src/messages.ts`，**禁止**硬编码中/英文到组件

完整规则见 `CLAUDE.md §5 + §7`。

---

## 6. 当前真实状态（从哪里来）

### 6.1 已完成（验证过的）

**后端**：Maven 7 模块骨架、统一 ApiResponse / DomainException / ErrorCode /
GlobalExceptionHandler、JWT 双 token、`api-contract` 集中 DTO、Flyway V1–V7 迁移、
Gateway `/api/v1/**` + TraceId + CORS、AI service OpenAI 兼容 provider + 配额 +
出题 draft 流、judge-worker RabbitMQ 消费者 + Audit + Non-retryable 异常、
测试包分片上传 + sha256 + 版本激活、**跨主机 testcase blob HTTP 分发**、
**真实 SandboxClient → go-judge /run**、**stdout/stderr/exitStatus/runTimeMillis**
落库 + 详情展示。

**学生端 `web-user`**：登录注册、首页、题库筛选、做题工作台（SplitPane + 多语言模板）、
提交记录、AI 辅导 + 题内 AI 抽屉、个人中心、SSE 流式 AI + Markdown 渲染、中英 i18n、
蓝白校园风格令牌。

**管理端 `web-admin`**：登录（无 tab）+ 独立注册路由、**全局会话过期监听挂 router
顶层**、Dashboard、用户/角色 CRUD、题库 + `ProblemEditorDrawer`（基础信息/题面与样例/
测试包三 Tab，Markdown 编辑器集成）、测试包分片上传 + 进度 + 版本激活、AI 草稿生成/
审核/导入。

**工程**：Docker Compose（MySQL/Redis/RabbitMQ/Nacos/Sentinel）联调通过、`mvn package`
和 `npm run build` 多轮验证通过、CLAUDE.md / HANDOVER.md / ROADMAP.md 完整。

### 6.2 进行中 / 部分完成

| 项 | 状态 | 落地缺口 |
|---|---|---|
| 真实隔离沙箱（Phase 5） | ✅ 已完成 | `deploy/sandbox/Dockerfile` 已提供 Python / C++ / Java runtime；2026-05-29 已用隔离 MySQL/RabbitMQ/sandbox 跑通完整提交链路 |
| AI 会话历史持久化 | 🟡 前端有 store/localStorage | 后端 `AiConversationEntity` 缺 `mode/source/problemTitle/problemTags` |
| `packages/ui` 复用层 | 🟡 偏薄 | `PageHeader/EmptyState/StatusChip/DifficultyChip` 仍散在两端 |
| 管理端样式统一 | 🟡 推进中 | 用户/角色/AI 草稿筛选、表格密度、空状态、移动端继续收敛 |
| `api-client/src/index.ts` 509 行 | 🟡 待拆 | 拆成 `types.ts / http.ts / endpoints/*.ts` |

### 6.3 工作树状态（重要）

Phase 1–5 沙箱主链路已入仓。接手第一件事仍然是先跑 `git status` 和
`git log -3 --oneline`，确认本地没有遗留脏改动，再继续 Phase 1 用户组或
测试保护工作。

### 6.4 已知风险与坑

- **测试用例包子域 = "重力井"**：前 10 God Nodes 占 5 席（`TestcasePackageEntity` /
  `TestcasePackageService` / `TestcaseUploadSessionEntity` / `TestcasePackageCaseEntity` /
  `LocalTestcaseStorageService`）。任何改动先 graphify path 检查影响面
- **雪花 ID 精度**：前端必当 string，已在 `api-client` 拦截。新端点要复用
- **Flyway 半成功**：MySQL DDL 不在事务，处置 SOP 见 `deployment.md §「Flyway 迁移失败的恢复 SOP」`
- **Arco a-alert `:content` 渲染异常**：必须 default slot，全仓 2026-05-25 已统一
- **判题主机 privileged sandbox**：必须独立主机、最小开放端口、内核版本受控、定期重建镜像
- **stale token 旁路**：调用注册类公开接口前要 `authStore.clear()`，RegisterView 已遵循

完整列表见 `HANDOVER.md §5`。

---

## 7. 接下来的路线（往哪里去）

### 7.1 30 天目标：收尾 Phase 0 → 进入 Phase 1

**Week 1–2 · 收尾 Phase 5（沙箱多语言镜像 + E2E）**

- [x] 重构 `deploy/sandbox/Dockerfile`，固化包含 `/usr/bin/python3` / `/usr/bin/g++` /
      `/usr/bin/javac` / `/usr/bin/java` 的可复现镜像
- [x] 启 Phase 5 任务：由 Codex 直接设计并实施 multi-language runtime image
- [x] 端到端联调清单（手动，2026-05-29）：
  - Python AC → `ACCEPTED`，`timeMillis=14`、`memoryKb=10996`、`runTimeMillis=35`
  - C++ AC → `ACCEPTED`
  - Java `Main.java` 单类 → `ACCEPTED`
  - C++ 编译错误 → `COMPILE_ERROR` + `stderrExcerpt`
  - Python `import sys; sys.exit(7)` → `RUNTIME_ERROR` + `exitStatus=7`
  - Python `while True: pass` → `TIME_LIMIT_EXCEEDED`
  - 大量 stdout → `OUTPUT_LIMIT_EXCEEDED`
  - 学生端题目详情"我的提交"tab 与 `/submissions` 详情弹窗均已浏览器验证
- [x] Flyway V1–V7 在隔离 MySQL `8.4` 干净库上由 auth-service 跑通
- [x] 2026-05-30 本地 IDEA + Docker sandbox 联调：补齐 `AIOJ_INTERNAL_API_TOKEN`
      与 `PROBLEM_SERVICE_BASE_URL=http://127.0.0.1:8202` 默认值，修复
      judge-worker 拉取 testcase blob 失败导致的 `SYSTEM_ERROR`

**Week 3 · 大测试点压测 + Ubuntu 24 部署验收**

- [ ] 50MB / 100MB zip 上传（分片 + 断点续传 + sha256）→ 评测全链路
- [ ] 非法路径 / 缺包 / 坏包 → `SYSTEM_ERROR` 审计链路完整
- [ ] worker 缓存命中率 / cache 清理策略
- [ ] Ubuntu 24 从 0 部署：Docker / Nginx HTTPS / secrets / Nacos & Sentinel 生产鉴权 /
      Flyway 顺序 / 多实例启动顺序 / 外部备份恢复演练

**Week 4 · 关键 service 单测托底**

- [ ] `JudgeTaskListener`（消费 → 调 sandbox → 写 audit 的最小成功 + 主要异常路径）
- [ ] `TestcasePackageValidator`
- [ ] `UserAccountService`
- [ ] `JwtTokenService`
- [ ] `backend/**/src/test/` 目前为空，先在 problem-service / judge-worker 建测试模块骨架

### 7.2 60-90 天目标：Phase 1 用户组 + Phase 2 题目集

- **Phase 1 用户组**（归 `auth-service`，复用 `UserAccountService`）：
  - V8/V9 迁移：`user_group` + `user_group_member`
  - API：`/api/v1/groups/**` + `/api/v1/me/groups`
  - 管理端「用户组」一级导航 + 学生端「我的小组」+ 邀请码加入
- **Phase 2 题目集 + 推送**（归 `problem-service`，同库避免跨库事务）：
  - V10/V11/V12：`problem_set` + `problem_set_item` + `problem_set_assignment`
  - 教师创建题集 → 选题排序 + 分数 → 分派到组（时间窗 + 计分策略）
  - 学生 Dashboard「正在进行的任务」+ 题目详情提示 assignment + 教师小组榜单

详细数据模型 / API / 验收见 `ROADMAP.md`。

### 7.3 长期穿插（不阻塞主线）

- `packages/ui` 下沉：`PageHeader / EmptyState / StatusChip / DifficultyChip / BaseCard / ConfirmDialog`
- `api-client` 拆分：`types.ts / http.ts / endpoints/*.ts`
- OpenAPI → api-client 自动生成（`npm run gen:api` from `/v3/api-docs`）
- Playwright e2e：登录 → 做题 → 提交 → 评测最小闭环
- 性能：Submission 列表 / 大题集分页 / Dashboard 聚合查询的 N+1 检查

---

## 8. 协作工作流

### 8.1 Codex 直接负责（推荐主流程）

> 自 2026-05-29 起，Codex 作为项目负责人直接承担需求澄清、方案设计、
> 代码实现、验证和汇报。历史 `docs/codex-exchange/` 保留为阶段化任务证据，
> 但新任务不再要求先写 inbox/outbox。

**主流程**：
1. Codex 先读 `PROJECT_HANDOFF.md`、`HANDOVER.md`、`ROADMAP.md` 和相关代码
2. 对高风险或产品取舍问题先提出判断与备选方案
3. 用户确认后，Codex 直接实现、验证、提交，并用简明报告说明结果
4. 功能推进后同步更新活文档，特别是 `HANDOVER.md` 与 `ROADMAP.md`

**历史归档**：`docs/codex-exchange/inbox/` 与 `outbox/` 只用于追溯 2026-05
前后 Claude 设计 / Codex 执行的任务过程；除非用户明确要求，不再新建 exchange 文件。

### 8.2 大任务拆 Phase 纪律（已成型规范）

> 见全局 lesson `~/.claude/memory/lessons/design/large-task-phase-discipline.md`

大功能（30+ 文件、几千行、数小时工作量）**必须**拆 3-5 个 phase：
- 每个 phase 有清晰目标、文件范围、验收命令和人工验收清单
- 每 phase 改动面 **< 15 文件 / < 1000 行**，超出就再拆
- 严格序列：前一 phase 验收通过 + 必要时实测，才开下一 phase
- 首 phase 必须 read-only 调研（搭建上下文共识 + 列 open questions + 拍板设计）
- 关键决策点用 AskUserQuestion 拍板，避免一路猜测累积错误
- 每 phase 都明示"完成后的系统状态"（能否编译、能否跑、有何 known gap）

**案例**：本项目 2026-05-28 沙箱模块拆 5 phase（基础设施 / cross-host blob / DTO+enum /
SandboxClient 真实实现 / multi-language image），前 4 phase 已 done，第 5 phase 是下一目标。

### 8.3 跨项目 Lessons 库

`~/.claude/memory/lessons/` 是所有项目共享教训库，避免重复踩坑。

**每次新会话启动 Codex 应**：
1. 读 `~/.claude/memory/lessons/INDEX.md`，扫当前任务关键词是否命中已有 lesson
2. 命中即把 Counter-rules 转化为当前任务的调研/证伪步骤或硬约束

**已命中本项目的 lessons**：
- 视觉/CSS 布局类任务 → `~/.claude/memory/lessons/debugging/visual-css-bugs.md`
  （案例：ProblemEditorDrawer 三 tab 内容不铺满，三轮才修对）
- 大任务拆 phase → `~/.claude/memory/lessons/design/large-task-phase-discipline.md`

**沉淀新教训**：本项目内任何"多轮失败 + 用户复盘"结束时，调用 skill `/distill-lesson`
把通用部分写入全局库，项目特定部分才写到 `CLAUDE.md`。

### 8.4 graphify 知识图谱

```bash
graphify update .                              # 修改后跑一次
graphify query "TestcasePackage 怎么从上传走到判题"
graphify path "JudgeTaskListener" "TestcasePackageEntity"
graphify explain "TestcasePackageService"
```

**改动 God Node 前**强烈建议先 `graphify path` 查影响面。输出在 `graphify-out/`，不入仓。

---

## 9. 部署与运行速查

### 9.1 本地启动

```powershell
# 1. 起基础设施
docker compose -f deploy/compose.yml --profile infra up -d

# 2. 后端（推荐分模块跑）
cd backend
mvn clean package
mvn -pl gateway-service spring-boot:run
mvn -pl auth-service spring-boot:run
mvn -pl problem-service spring-boot:run
mvn -pl judge-worker spring-boot:run
mvn -pl ai-service spring-boot:run

# 3. 前端
npm install            # 根目录一次
npm run dev:user       # :5173
npm run dev:admin      # :5174
```

### 9.2 改完代码必跑 Checklist

**前端**：`npm run typecheck`（任一改动的 workspace）；影响 API 客户端 → 同步检查 user + admin 两端 build

**后端**：`mvn -pl <module> compile`；改 `api-contract` → 所有依赖它的服务重新编译；新表/字段 → 必新建 V{N+1}__*.sql；改 controller → 过一遍 `/swagger-ui.html`

**提交前**：搜 `System.out.println` / `console.log(` 应 0 匹配；搜 `change-me` / `replace-` 应 0 匹配；`apps/*/dev-server*.log` 不要进 git

### 9.3 调试入口

| 项 | 入口 |
|---|---|
| Trace ID | 响应头 `X-Trace-Id`，跨服务日志 grep 这个串调用 |
| Actuator | `/actuator/health` `/actuator/prometheus` `/actuator/metrics` |
| RabbitMQ Management | `localhost:15672` |
| Nacos 控制台 | `localhost:8080` |
| Sentinel 控制台 | `localhost:8858` |
| Swagger | `<service>/swagger-ui.html` |

### 9.4 生产部署形态

详见 `deployment.md §3`。要点：
- 后端主机仅 gateway 暴露公网；MySQL/Redis/RabbitMQ/Nacos 留内网
- 判题主机独立硬件，sandbox container `privileged: true`
- secrets 走 Docker secrets / host-mounted file，**不**写 .env
- Flyway：只 1 个实例 `FLYWAY_ENABLED=true` 跑迁移，其他后启

---

## 10. 关键文件锚点（出问题先看这些）

### 10.1 后端核心

| 模块 | 入口 / 核心类 |
|---|---|
| 通用响应 / 错误码 | `common-lib/api/ApiResponse.java` · `common-lib/error/ErrorCode.java` · `DomainException.java` · `GlobalExceptionHandler.java` |
| JWT / 鉴权 Filter | `common-lib/security/BearerTokenAuthenticationFilter.java` · `InternalApiTokenFilter.java` · `JwtTokenService.java` |
| 判题消费者 | `judge-worker/consumer/JudgeTaskListener.java` |
| 沙箱客户端 | `judge-worker/domain/SandboxClient.java` |
| 输出比较器 | `judge-worker/domain/DefaultOutputComparator.java` |
| 测试包 Blob 客户端 | `judge-worker/domain/TestcaseBlobClient.java` |
| 测试包 Service | `problem-service/domain/testcase/TestcasePackageService.java` |
| 测试包内部下载端点 | `problem-service/controller/InternalTestcaseController.java` |
| 提交服务 | `problem-service/domain/SubmissionService.java`（含 publishAfterCommit 模式） |
| 审判服务 | `judge-worker/domain/SubmissionJudgingService.java` |
| 沙箱 runtime 字段迁移 | `api-contract/.../db/migration/V7__sandbox_runtime_fields.sql` |
| 沙箱镜像 | `deploy/sandbox/Dockerfile` + `deploy/compose.yml#sandbox` |
| AI Provider | `ai-service/domain/AiProvider.java` + `OpenAiCompatibleProvider.java` |

### 10.2 前端核心

| 项 | 路径 |
|---|---|
| API 客户端 + ApiError + JWT 刷新 + SSE + 雪花 ID 拦截 | `packages/api-client/src/index.ts` |
| i18n 双语 + `errors.*` 映射 | `packages/i18n/src/messages.ts` |
| 学生端路由 | `apps/web-user/src/router/index.ts` |
| 管理端路由 + 全局会话过期监听 | `apps/web-admin/src/router/index.ts` |
| 题目编辑器（God Drawer） | `apps/web-admin/src/components/ProblemEditorDrawer.vue` |
| 测试包上传 UI | `apps/web-admin/src/components/TestcasePackageUploader.vue` |
| 提交详情 Modal | `apps/web-user/src/components/submission/SubmissionDetailModal.vue` |

### 10.3 God Nodes（改动前必须三思）

来自 graphify 前 10 God Nodes 分析：

1. **TestcasePackage 子域** 占 5 席：`TestcasePackageEntity` / `TestcasePackageService` /
   `TestcaseUploadSessionEntity` / `TestcasePackageCaseEntity` / `LocalTestcaseStorageService` /
   `TestcasePackageValidator`
2. **共享工厂** `ApiResponse.ok()` 被 7 个 controller 共用 —— 签名变更影响全后端
3. **跨服务核心实体**：`SubmissionEntity` / `ProblemEntity` / `ProblemDraftEntity` /
   `UserAccountService`

---

## 11. 风险清单（按严重度排序）

| 风险 | 严重度 | 缓解 |
|---|---|---|
| 后端单测为空 | 🔴 P0 | 任何重构都没有保护网；先补 4 个关键 service |
| 大测试点压测未做 | 🟠 P1 | 50MB/100MB zip 可能在分片 / sha256 / worker 缓存路径出问题 |
| Ubuntu 24 生产部署未验收 | 🟠 P1 | secrets / Flyway 顺序 / 多实例启动顺序需要专项 |
| `api-client/src/index.ts` 509 行 | 🟡 P2 | 改动易撞锁；拆 types/http/endpoints |
| AI 会话历史模型不完整 | 🟡 P2 | 后端缺 `mode/source/problemTitle/problemTags`，前端用 localStorage 兜底 |
| `packages/ui` 偏薄 | 🟡 P2 | 6 个组件分散在 user/admin 双写 |
| 无 e2e 自动化 | 🟡 P2 | 登录→做题→提交→评测的闭环无回归保护 |
| 判题主机 privileged sandbox | 🟡 持续 | 必须独立主机、最小开放端口、内核版本受控 |

---

## 12. 上手 7 天清单

### Day 1 · 读文档 + 建心智模型
- [ ] 读本文档（你正在读）
- [ ] 读 `CLAUDE.md`（红线 / 约定 / 禁忌）
- [ ] 读 `HANDOVER.md`（详细现状）
- [ ] 读 `ROADMAP.md`（详细路线）
- [ ] 读 `architecture.md` + `deployment.md`

### Day 2 · 本地跑起来
- [ ] `docker compose --profile infra up -d` 起基础设施
- [ ] 后端 5 个服务 `mvn spring-boot:run` 起来
- [ ] 前端 `npm run dev:user` + `npm run dev:admin` 起来
- [ ] 跑一遍：注册学生 → 题库浏览 → 进题做题 → 提交 → 看提交记录
- [ ] 跑一遍：管理员登录 → 题库 → 新建一道题 → 上传测试包 → 激活 → AI 草稿生成

### Day 3 · 摸沙箱 + 工作区入仓
- [ ] `git status` + `git diff --stat` 看 Phase 1–4 工作区改动
- [ ] 对照 4 份 outbox（`docs/codex-exchange/outbox/2026-05-28-*.md`）理解每 phase 做了什么
- [ ] 把工作区改动拆成 4 个 commit 入仓（按 phase 拆，便于后续追溯）
- [ ] 确认 `mvn package` + `npm run typecheck` 在 HEAD 上全绿

### Day 4 · 起 Phase 5
- [x] 由 Codex 直接启动 Phase 5 multi-language runtime image 任务
- [x] 调研 `deploy/sandbox/Dockerfile` 当前内容，列 Phase 5 需要补的 runtime 包清单
- [x] 设计并落地：基于 `eclipse-temurin:17-jdk-jammy` 承载 JDK17，
      追加 `g++` / `python3`，并把 JDK 复制到 `/usr/lib/jvm` 以适配 go-judge `/usr` 挂载
- [x] 起 Phase 5 并完成验收

### Day 5 · 跑 E2E 联调 6 条用例
已按 §7.1 Week 1–2 清单跑通 7 条 sandbox 用例（含可选输出超限）。

### Day 6 · 补 4 个关键 service 单测骨架
- [ ] 在 `backend/problem-service/src/test/java` 和 `backend/judge-worker/src/test/java`
      建测试模块骨架
- [ ] 给 `JudgeTaskListener` / `TestcasePackageValidator` / `UserAccountService` /
      `JwtTokenService` 各加 1 个 happy path + 1 个主要异常 path 测试

### Day 7 · 复盘 + 更新文档
- [x] 更新 `HANDOVER.md §2` 把 Phase 5 移到已完成
- [x] 更新 `ROADMAP.md` Phase 0 状态
- [x] 在本文档 §6.3 把"Phase 1–4 工作区未提交"标记移除
- [ ] 如有跨项目可复用教训，调用 `/distill-lesson` 沉淀

---

## 13. 文档生态地图（哪份文档解决什么问题）

| 你想知道 | 看哪份 |
|---|---|
| 项目是什么 + 现在到哪 + 我接下来做什么 | **本文档** |
| 红线规则 / 编码约定 / 禁忌 | `CLAUDE.md` |
| 真实当前状态快照（活文档，每次推送更新） | `docs/HANDOVER.md` |
| 阶段化路线图 + 各 phase 数据模型 / API | `docs/ROADMAP.md` |
| 服务边界 / AI 治理理念 | `docs/architecture.md` |
| 三种部署形态 / 跨主机顺序 / Flyway SOP / 安全约束 | `docs/deployment.md` |
| Git / UI / 本地环境细节 | `docs/DEVELOPMENT.md` |
| 运维监控 / 告警 / 日志 | `docs/operations.md` |
| 历史 Claude ↔ Codex 文件交换约定 | `docs/codex-exchange/README.md` |
| 历史 Codex 提示词（设计意图） | `docs/codex-prompts/` |
| 各 phase 实际改了什么 | `docs/codex-exchange/inbox/` + `outbox/` |
| 跨项目教训库 | `~/.claude/memory/lessons/INDEX.md` |

---

## 14. 维护本文档

- **30 天目标变更** → 更新 §7.1
- **Phase 推进** → 更新 §6 + §7.1 状态
- **新增 God Node / 关键文件** → 更新 §10
- **新风险出现** → 更新 §11
- **新人入职用过 7 天清单** → 根据反馈调整 §12

**本文档是给"项目负责人"看的，不是日报**。改动频率应当 < 一周一次，每次改动都
应当是"路线 / 现状 / 风险"层面的，而不是某个具体 commit 层面的。具体 commit 写
`HANDOVER.md §7`。

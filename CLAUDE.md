# AI-OJ Next — Project Guide

> 校园教学型 AI 在线评测系统 v2。本文件是项目唯一权威的"快速上手 + 约定 + 红线"指南。
> 每个 Claude / 协作者会话启动时都应先读完本文件。

## 1. Project Identity

**定位**：教学场景的 OJ（不是 ACM 竞赛 OJ），核心差异点是 **AI 助教 + AI 出题（需教师审核）+ 配额治理**。

**核心非功能性约束**：
- 评测必须在隔离沙箱内运行（`SANDBOX_ENDPOINT`），永远不在 judge-worker 进程内直接执行用户代码。
- AI 内容必须可审计：每条调用记 `AiUsageRecord`，每个出题草稿走 draft → approve → import。
- 用户数据可外泄面要小：仅 gateway 暴露在公网，后端服务和中间件留在内网。

## 2. Tech Stack（精确到次版本，升级前先讨论）

| 层 | 选型 |
|---|---|
| JDK | 17（**不要**降到 1.8） |
| Spring Boot | 3.5.14 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| MyBatis-Plus | 3.5.16 |
| JJWT | 0.12.6 |
| SpringDoc OpenAPI | 2.8.17 |
| MySQL | 8 |
| Redis | 7 |
| RabbitMQ | latest 3.x |
| Nacos | 3.x |
| Vue | 3.5.34 |
| Vite | 8.0.13 |
| TypeScript | 5.9.3 |
| Pinia | 3.0.4 |
| Arco Design Vue | 2.58.0 |
| Node 包管理 | **npm workspaces**（不是 pnpm 不是 yarn 不是 turbo） |

## 3. Repository Layout

```
backend/                  Maven 多模块 (parent pom.xml)
  common-lib/             跨服务共享：ApiResponse、ErrorCode、JWT、TraceId、Security 过滤器
  api-contract/           跨服务 DTO + Flyway 迁移（resources/db/migration/V*.sql）
  gateway-service/        :8101  Spring Cloud Gateway (WebFlux)，CORS + TraceId + Sentinel
  auth-service/           :8201  用户、角色、登录、注册、JWT 刷新
  problem-service/        :8202  题目、提交、测试用例包（分片上传）、判题任务发布
  judge-worker/           :8203  RabbitMQ 消费者，调沙箱评测，可横向扩 N
  ai-service/             :8204  AI 对话、AI 出题草稿、配额、OpenAI 兼容协议
apps/
  web-user/               :5173  学生端 SPA
  web-admin/              :5174  教师/管理员端 SPA
packages/
  api-client/             浏览器 API 客户端（fetch + JWT 自动刷新 + SSE）
  ui/                     共享 Vue 组件（目前仅 OjStat / OjToolbar，待扩充）
  i18n/                   国际化 messages + 语言切换器
deploy/                   Docker Compose、Nginx、Dockerfile、secrets 示例
docs/                     架构、部署、运维文档
graphify-out/             知识图谱（.gitignore，本地用 graphify update . 生成）
```

## 4. Architecture Map

```
                Browser (5173 / 5174)
                       │
                       ▼
            gateway-service :8101
              │   │   │   │
   ┌──────────┘   │   │   └────────────┐
   ▼              ▼   ▼                ▼
 auth-svc   problem-svc   ai-svc   /api/v1/submissions
 :8201        :8202        :8204       │
                │                      │ (publish RabbitMQ)
                └──────────────┬───────┘
                               ▼
                       judge.tasks queue
                               │
                               ▼
                     judge-worker :8203 ×N
                               │
                               ▼
                       Sandbox HTTP（隔离主机）
```

**通信约定**：
- 浏览器只调 gateway，**禁止**直连 :8201/:8202/:8203/:8204。
- problem-service 触发判题 → **只通过 RabbitMQ**，**禁止** HTTP 调用 judge-worker。
- ai-service 引用题目数据通过 `ProblemServiceClient`（HTTP），不直接读 problem-service 的库。
- 所有跨服务 DTO **必须**放在 `api-contract`，禁止在业务服务内重复声明。

## 5. Conventions（红线规则）

### 5.1 API 响应
- 后端所有 controller 返回 `ApiResponse<T>`（`code: int, message: string, data: T, traceId, timestamp`）。
- `code = 0` 才是成功。其他 code 见 `com.aioj.next.common.error.ErrorCode`。
- 错误抛 `DomainException(ErrorCode, message)`，由 `GlobalExceptionHandler` 统一转换。

### 5.2 路由前缀
- 所有公开 API 必须在 `/api/v1/...` 下，**先在 gateway 的路由配置里加前缀**，再在业务服务实现。
- 现有前缀：`/api/v1/auth/**`、`/api/v1/users/**`、`/api/v1/admin/**`、`/api/v1/problems/**`、`/api/v1/submissions/**`、`/api/v1/ai/**`。

### 5.3 ID 类型
- 后端 ID 是 Long（雪花算法）。
- **前端必须把 16+ 位的 `*Id` / `id` 字段当字符串处理**——`packages/api-client/src/index.ts` 里有 `preserveLargeIntegerIds()` 拦截解析。新增 API 客户端代码时复用这个机制。
- 类型用 `EntityId = string`。

### 5.4 鉴权
- 双 token：`accessToken` 短期（2h）、`refreshToken` 长期（14d）。
- 前端 `request()` 遇到 401 自动用 refresh token 重试一次，再失败抛 `aioj:auth-expired` 事件。
- 服务端校验由 `common-lib/BearerTokenAuthenticationFilter` 统一做，不要在 controller 里手写。

### 5.5 数据库迁移
- 用 Flyway。脚本放 `api-contract/src/main/resources/db/migration/V{N}__{name}.sql`。
- **只往后加，禁止改历史 V*.sql 内容**。
- 字段命名 snake_case。表名单数（`user`、`problem`、`submission`）。
- 部署时让**一个**实例先跑迁移，其他实例后启动（详见 docs/deployment.md）。

### 5.6 AI 模块
- 出题：必须走 draft → admin approve → import 三步，禁止直接调 problem-service 插题库。
- 对话：必须先写 `AiUsageRecord` 和扣 `AiQuotaPolicy`，超额返回特定错误码。
- Provider 接入：实现 `AiProvider` 接口；新增 provider 不要改 controller 层。
- 流式输出走 SSE（`/api/v1/ai/chat/stream`），事件名规范：`meta` / `message` / `done` / `error`。

### 5.7 沙箱
- judge-worker 永远调 `SANDBOX_ENDPOINT`，不在自己进程内执行用户代码。
- 沙箱凭证用 `SANDBOX_TOKEN`，从 Docker secrets 注入，不写代码或 .env。
- 异常区分：可重试 vs `NonRetryableJudgeTaskException`（脏数据，直接死信）。

### 5.8 前端约定
- 视图层 (`views/`) 不直接 fetch，统一走 `@aioj/api-client` 导出的 `api` 对象。
- 跨页面状态用 Pinia store（`stores/auth.ts` 是范例）。
- 共享 UI 组件下沉到 `@aioj/ui`，**不**在 user/admin 各自重写一遍。
- 全局样式分层：`tokens.css`（设计令牌） → `global.css`（基础） → `layout.css`（布局） → 组件 scoped 样式。
- i18n 文案放 `@aioj/i18n/src/messages.ts`，禁止硬编码中文/英文到组件。

## 6. Common Tasks

### 6.1 启动顺序（本地开发）
```powershell
# 1. 起基础设施（MySQL/Redis/RabbitMQ/Nacos/Sentinel）
docker compose -f deploy/compose.yml --profile infra up -d

# 2. 后端（每个服务自己 mvn spring-boot:run，或一次性打包）
cd backend
mvn clean package
# 推荐分别在各模块开启动：mvn -pl gateway-service spring-boot:run

# 3. 前端
npm install                          # 根目录跑一次
npm run dev:user                     # 学生端 :5173
npm run dev:admin                    # 管理员端 :5174
```

### 6.2 修改代码后的 Checklist（必跑）

**前端改动**：
- [ ] `npm run typecheck`（任一改动的 workspace 必须通过）
- [ ] 影响 API 客户端 → 同步检查 `web-user` 和 `web-admin` 两端 build

**后端改动**：
- [ ] `mvn -pl <module> compile` 至少通过
- [ ] 改了 `api-contract` → **所有**依赖它的服务都要重新编译验证
- [ ] 加了新表/字段 → 必须新建 `V{N+1}__*.sql`，禁止改历史
- [ ] 涉及 controller → 用 SpringDoc UI（`/swagger-ui.html`）目测一遍

**提交前**：
- [ ] 没有日志泄露（搜 `System.out.println`、`console.log(`，应 0 匹配）
- [ ] 没有硬编码 secret（搜 `change-me`、`replace-`）
- [ ] `apps/*/dev-server*.log` 不要进 git

### 6.3 调试入口
- Trace ID：每个请求响应头有 `X-Trace-Id`，跨服务日志 grep 这个就能串起一次完整调用。
- 后端 Actuator：`/actuator/health`、`/actuator/prometheus`、`/actuator/metrics`。
- RabbitMQ Management UI: `localhost:15672`。
- Nacos 控制台: `localhost:8080`。
- Sentinel 控制台: `localhost:8858`。

### 6.4 知识图谱
```bash
graphify update .                     # 重建（修改后跑一次）
graphify query "问题"                  # 自然语言查询
graphify path "ClassA" "ClassB"        # 查依赖路径
graphify explain "TestcasePackageService"   # 解释一个概念
```
图谱输出在 `graphify-out/`，不入仓。

## 7. 禁忌速查

| 不要 | 替代做法 |
|---|---|
| 在 controller 抛裸 RuntimeException | 抛 `DomainException(ErrorCode, msg)` |
| 在 service 之间用 HTTP 触发判题 | 发 `JudgeTaskMessage` 到 RabbitMQ |
| 前端硬编码后端服务地址 | 用 `VITE_API_BASE_URL` + gateway 前缀 |
| 改历史 Flyway 迁移 | 新增 V{N+1}__*.sql |
| AI 出题直接入库 | 走 ProblemDraft 审核流 |
| 在 user/admin 重复实现同一个组件 | 下沉到 `@aioj/ui` |
| `import.meta.env.VITE_*` 之外读环境变量 | 走 `apiBaseUrl()` |
| 把 16+ 位 ID 当 number 解析 | 用 `preserveLargeIntegerIds` |

## 8. 项目核心抽象（God Nodes，来自 graphify 分析）

改动以下任意一处需特别谨慎，建议先有单测/集成测试保护：

1. **TestcasePackage 子域**（前 10 God Nodes 占 5 席，是当前最重领域）：
   - `TestcasePackageEntity` / `TestcasePackageService` / `TestcaseUploadSessionEntity`
   - `TestcasePackageCaseEntity` / `LocalTestcaseStorageService` / `TestcasePackageValidator`
   - 涉及：分片上传、sha256 校验、版本激活、Judge 端缓存
2. **共享工厂**：`ApiResponse.ok()` 被 7 个 controller 共用——签名变更影响全后端
3. **跨服务核心实体**：`SubmissionEntity`、`ProblemEntity`、`ProblemDraftEntity`、`UserAccountService`

查询命令：
- `graphify query "TestcasePackage 怎么从上传走到判题"`
- `graphify path "JudgeTaskListener" "TestcasePackageEntity"`
- `graphify explain "<某个类>"`

## 9. Companion Docs（每个新 Claude 会话也要看一眼）

| 文件 | 内容 | 何时读 |
|---|---|---|
| `docs/HANDOVER.md` | 当前真实进度快照（已完成 / 待补 / 风险） | **每次会话启动必读**——是项目"现状"基准 |
| `docs/ROADMAP.md` | 阶段化路线图（含用户组等扩展规划） | 接到新任务时核对优先级 |
| `docs/DEVELOPMENT.md` | 完整开发习惯（Git、UI、本地环境等细节） | 提交、改 UI、改环境前 |
| `docs/codex-prompts/` | 历史 + 待执行的 Codex 提示词（`.txt`） | 看历次改动的设计意图 |

工作流：本会话设计 → 我输出 `.txt` 提示词 → 用户转交 Codex 执行 → 用户回传结果。HANDOVER 和 ROADMAP 是**活文档**，每次有功能落地或推进，更新对应条目。

# AI-OJ Next — Handover Snapshot

> 这是当前项目状态的**活文档**。每次有功能落地、commit 推送、修 bug，**对应条目即时更新**。
> 新会话/新负责人接手时，先读 `CLAUDE.md`（约定/红线），再读本文件（现状）。
>
> **快照时间**：2026-05-29 · **基线 commit**：`8b38a13 introduce typed ApiError and form-level field error binding` · **分支**：`master`

---

## 1. 项目定位（一句话）

校园**教学型** AI 在线评测系统 v2。不是 ACM 竞赛 OJ。差异点：**学生刷题 + 教师/管理员维护题库 + AI 助教按题辅导 + AI 出题草稿需人工审核 + 配额与审计治理**。

## 2. 已完成（验证过的）

### 后端
- [x] Maven 多模块骨架（7 个模块）—— `common-lib` / `api-contract` / `gateway-service` / `auth-service` / `problem-service` / `judge-worker` / `ai-service`
- [x] 统一 `ApiResponse<T>`、`DomainException`、`ErrorCode`、`GlobalExceptionHandler`
- [x] JWT 双 token（access 2h / refresh 14d）+ refresh token 哈希存储 + 吊销
- [x] 跨服务 DTO 集中在 `api-contract`，Flyway 4 版迁移（baseline → testcase_package_storage）
- [x] Gateway `/api/v1/**` 路由、TraceId、CORS
- [x] BearerTokenAuthenticationFilter 对无效 token 宽容（clear context 而非 401）
- [x] AI service：OpenAI 兼容 provider + ProblemServiceClient + AiUsageRecord/AiQuotaPolicy + 出题 draft → approve → import 流程
- [x] judge-worker RabbitMQ 消费者骨架 + JudgeAuditLog + NonRetryableJudgeTaskException
- [x] 测试用例分片上传 + sha256 校验 + 版本激活机制

### 学生端 (`web-user`)
- [x] 登录/注册（独立路由，已登录用户进 register 会被守卫弹开）
- [x] 首页（练习概览、最近提交、推荐题目、学习成就）
- [x] 题库 + 难度/标签筛选
- [x] 题目详情/做题工作台（自研 SplitPane + CodeEditorShell + 多语言模板）
- [x] 提交记录（过滤、状态着色、详情）
- [x] AI 辅导页 + 题内 AI 辅助抽屉 + AI 历史会话体系
- [x] 个人中心（资料、改密、登出）
- [x] SSE 流式 AI 输出 + Markdown 渲染 + 流中换行修复
- [x] 中英文 i18n 双语切换
- [x] 蓝白校园练习平台风格设计令牌

### 管理端 (`web-admin`)
- [x] 登录页（无 Tab 切换，注册移到独立 `/register` 路由）
- [x] 注册页独立 + 注册成功跳回登录带 `?registered=1&account=...` + 顶部 alert + account 预填
- [x] 会话过期事件**全局**监听（移出 AppShell，挂在 router 模块顶层）→ 任何路由下 401 都能正确弹回登录
- [x] 路由守卫 `loadProfile(true)` —— 每次切换强校验，规避 store cache 屏蔽
- [x] Dashboard
- [x] 用户管理（CRUD + 角色 + 启用/禁用 + 软禁）
- [x] 角色权限矩阵
- [x] 题库管理 + ProblemEditorDrawer（基础信息/题面与样例/测试包三 Tab，最近精修过）
- [x] 测试包上传入口（分片 + 进度 + 状态轮询 + 版本激活）
- [x] AI 草稿生成/审核/导入

### 平台 / 工程
- [x] `CLAUDE.md` + `graphify-out/`（gitignore）+ `.gitignore` 收敛日志/产物
- [x] 本地 Docker Compose: MySQL 8 / Redis 7 / RabbitMQ / Nacos / Sentinel 联调通过
- [x] 后端 `mvn clean package` + 前端 `npm run build` 多轮验证通过
- [x] 项目协作模式切换为 Codex 直接负责；`docs/codex-exchange/` 保留为历史归档

## 3. 进行中 / 部分完成

| 项 | 状态 | 备注 |
|---|---|---|
| AI 会话历史持久化 | 🟡 部分 | 前端有 localStorage/store；后端会话模型缺少 `mode/source/problemTitle/problemTags` 等元数据 |
| `packages/ui` 复用层 | 🟡 偏薄 | 目前只 `OjStat` + `OjToolbar`；`PageHeader/EmptyState/StatusChip/DifficultyChip` 还分散在 user/admin |
| 管理端样式统一 | 🟡 推进中 | 用户/角色/AI 草稿/题库的筛选、表格密度、空状态、移动端体验仍可继续收敛 |
| `api-client/src/index.ts` 单文件 509 行 | 🟡 待拆 | types / http / endpoints 应分离 |
| 真实隔离沙箱 | ✅ Phase 5 done | go-judge `/run`、testcase zip internal HTTP 分发、DTO/状态扩展、runtime 字段、Python/C++/Java sandbox image 与完整学生提交链路均已跑通 |

## 4. 待办（重要未完成）

| 项 | 优先级 | 风险 |
|---|---|---|
| **大测试点压测** | 🟠 P1 | 50MB/100MB zip、断点重试、非法路径、hash 校验、worker 缓存、缺包/坏包 `SYSTEM_ERROR` 审计链路需要全链路压一遍 |
| **Ubuntu 24 生产部署验收** | 🟠 P1 | Docker/Nginx/secrets/Nacos & Sentinel 生产鉴权/迁移顺序/多实例启动顺序需要专项验证 |
| 后端单测 | 🟡 P2 | `backend/**/src/test/` 为空。关键 service（JudgeTaskListener / TokenService / TestcasePackageValidator / UserAccountService）应先补 |
| OpenAPI → api-client 自动生成 | 🟡 P2 | springdoc 已就位，目前前后端契约靠手抄。可加 `npm run gen:api` |
| E2E 流程回归 | 🟡 P2 | 没有 Playwright 等 e2e，登录→做题→提交→评测→看结果的最小闭环没有自动化保护 |

## 5. 已知风险与坑

- **测试用例包子域是"重力井"**：前 10 God Nodes 占 5 席（详见 CLAUDE.md §8）。任何改动都要先有测试或 graphify path 检查影响面。
- **雪花 ID 精度**：所有 16+ 位 ID 在前端必须当 string，已在 `api-client` `preserveLargeIntegerIds()` 拦截。新增端点要复用此机制。
- **stale token 旁路**：`api-client.request()` 自动带 Authorization 头；调用注册类公开接口前应 `authStore.clear()`。RegisterView 已遵循。
- **Flyway 历史不可改**：只能新增 `V{N+1}__*.sql`。
- **判题永远过 RabbitMQ**：禁止 HTTP 直连 judge-worker。
- **AI 出题永远走 draft 流**：禁止直接调 problem-service 插题。
- **Flyway 半成功状态**：MySQL DDL 不在事务里，一条 V*.sql 多 DDL 时
  前面成功后面失败会留下"半完成 schema + flyway success=0"。处置 SOP
  见 docs/deployment.md «Flyway 迁移失败的恢复 SOP»。
- **Arco a-alert `:content` prop 渲染异常**：self-closing `<a-alert ... />`
  + `:content="X"` 在 Arco 2.58 + Vue 3.5 下会渲染「红框无文字」。规范见
  CLAUDE.md §5.8；全仓在 2026-05-25 已统一为 default slot 写法。
- **判题主机 privileged sandbox**：判题主机必须 `privileged: true` 跑
  sandbox container，不应与其他业务混部，主机层加固按 docs/deployment.md
  §3.4。
- **Phase 5 E2E 已验证但还缺自动化保护**：2026-05-29 已用隔离
  MySQL/RabbitMQ/sandbox 跑通 Python/C++/Java AC、C++ 编译错误、Python
  运行错误、TLE、OLE，并在学生端浏览器确认详情弹窗；后续仍需要把该
  流程沉淀为自动化 e2e 或 smoke 脚本。
- **IDEA 本地联调不要用 Docker 服务名**：2026-05-30 复现
  `Testcase package unavailable: failed to fetch testcase package blob`，根因是
  judge-worker 本地进程拉 problem-service blob 时必须使用
  `PROBLEM_SERVICE_BASE_URL=http://127.0.0.1:8202`，且与 problem-service 共用
  `AIOJ_INTERNAL_API_TOKEN=dev-internal-token`；直接 curl internal blob 返回
  `200` 后，重启 judge-worker 再提交即可恢复。

## 6. 关键文件锚点

| 模块 | 入口 / 核心类 |
|---|---|
| API 客户端 | `packages/api-client/src/index.ts` (`api`, `authStore`, `streamAi`, `preserveLargeIntegerIds`) |
| 学生端路由 | `apps/web-user/src/router/index.ts` |
| 管理端路由 + 全局会话过期监听 | `apps/web-admin/src/router/index.ts` |
| 判题消费者 | `backend/judge-worker/src/main/java/.../consumer/JudgeTaskListener.java` |
| 沙箱客户端 | `backend/judge-worker/src/main/java/.../domain/SandboxClient.java` |
| 输出比较器 | `backend/judge-worker/src/main/java/.../domain/DefaultOutputComparator.java` |
| 测试包 Blob 客户端 | `backend/judge-worker/src/main/java/.../domain/TestcaseBlobClient.java` |
| 沙箱服务 | `deploy/sandbox/Dockerfile` + `deploy/compose.yml#sandbox` |
| 沙箱 runtime 字段迁移 | `backend/api-contract/src/main/resources/db/migration/V7__sandbox_runtime_fields.sql` |
| 测试包 Service | `backend/problem-service/src/main/java/.../domain/testcase/TestcasePackageService.java` |
| 测试包内部下载端点 | `backend/problem-service/src/main/java/.../controller/InternalTestcaseController.java` |
| 内部调用鉴权 Filter | `backend/common-lib/src/main/java/.../security/InternalApiTokenFilter.java` |
| AI Provider | `backend/ai-service/src/main/java/.../domain/AiProvider.java` + `OpenAiCompatibleProvider.java` |

## 7. 近期 commit（HEAD 倒序）

```
8b38a13 introduce typed ApiError and form-level field error binding
d9a06c7 standardize backend exception handling and field errors
ce0ea95 docs fix table naming rule and log editor upgrade
230ede5 use markdown editor for problem statement and notes
a336f5d add problem notes field for student explanation
f92c141 refine problem editor density and sample layout
836d2e6 docs add handover roadmap and codex prompt workflow
1b1e89c refine admin dashboard accuracy and small UX bugs
5c09069 fix admin frontend correctness and session bugs
7012b57 refine admin problem editor tabs
2990d39 fix admin register routing and session expiry
80a3d07 docs add project guide and ignores
```

### 异常处理规范化（commits `d9a06c7` + `8b38a13`）

**触发**：用户实测发现，管理端创建题目时空样例 → 后端抛 `MethodArgumentNotValidException` → 旧 handler 仅 `ex.getMessage()` 把 Spring 的 toString（codes / arguments / default message 三段元数据）原样回前端 → 前端原样吐成顶部红条墙。**前端没拦 + 后端没结构化 + 前端展示没分类**三层都缺失。

**后端（`d9a06c7`）**：
- `ErrorCode` 扩展 9 个细粒度码：`VALIDATION_FAILED 40001` / `INVALID_PAYLOAD 40002` / `MISSING_PARAMETER 40003` / `TYPE_MISMATCH 40004` / `METHOD_NOT_ALLOWED 40500` / `PAYLOAD_TOO_LARGE 41300` / `TOO_MANY_REQUESTS 42900` / `INTERNAL_ERROR 50000` / `SERVICE_UNAVAILABLE 50300`
- `ApiResponse` 新增 `Object details` 字段（向后兼容，JSON 多一个 key），加 factory `failWithDetails(code, msg, details)`
- `GlobalExceptionHandler` 重写：11 个 `@ExceptionHandler` 分类覆盖 `DomainException` / `MethodArgumentNotValidException` / `ConstraintViolationException` / `BindException` / `HttpMessageNotReadableException` / `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` / `HttpRequestMethodNotSupportedException` / `HttpMediaTypeNotSupportedException` / `MaxUploadSizeExceededException` / catch-all
- 校验类异常自动把 `BindingResult.getFieldErrors()` 折叠成 `Map<String, String>` 进 `details`（字段路径 → defaultMessage）
- 兜底 handler 走 `log.error("Unhandled exception traceId={}", ...)` + 返回 `INTERNAL_ERROR` 通用消息，**永不**回传 `ex.getMessage()`

**前端（`8b38a13`）**：
- `@aioj/api-client` 新增 `ApiError extends Error` 类（`code` / `details` / `traceId` / `serverMessage` / `userMessage` getter / `fieldError(path)`）；`request()` 抛 `ApiError` 而非裸 `Error`
- 新增 `setApiErrorMessageResolver(fn)` 注入点；两端 `main.ts` 在 i18n 装好后注册解析器，让 `ApiError.userMessage` 自动走 i18n
- 新增 i18n `errors.*` 双语映射（14 个码 + `unknown` fallback）
- `ProblemEditorDrawer.vue` 接入字段级错误：`fieldError(path)` helper + `<a-form-item :validate-status :help>` 绑 title / statement / notes / timeLimitMillis / memoryLimitKb / `testCases[i].input` / `testCases[i].expectedOutput`；外加 `orphanedFieldErrors` 兜住未绑到字段的错误
- 空样例改为**硬阻断**（原来是 warning 后继续提交）
- 4 个 Login/Register 视图（admin + user）切到 `ApiError.userMessage` 显示

**兼容性**：`ApiResponse` JSON 加字段不破坏旧客户端；`ApiError extends Error` 所以现存所有 `catch (e) { e.message }` 仍能工作（serverMessage 作 fallback）。

### 题目编辑器升级（commits `f92c141` + `a336f5d` + `230ede5`）

### 题目编辑器升级（commits `f92c141` + `a336f5d` + `230ede5`）

**视觉重构**（`f92c141`）：
- 基础信息 tab：信息条 → inline 小字提示；卡片 padding 收紧；preview 侧栏密度提升；卡片 hover 抖动移除
- 题面与样例 tab：sample input/output 由并列改 stack（每个 textarea 从 ~170px → ~440px）；字符计数浮到 textarea 右下角

**Notes 字段全链路**（`a336f5d` 后端 + `230ede5` 前端）：
- Flyway V5 给 `problems` 表加 `notes TEXT NULL`（向后兼容，老题 NULL）
- 三端 DTO 同步：`ProblemCreateRequest` / `ProblemUpdateRequest` / `ProblemResponse` 加 `notes` 字段（`@Size(max=20000)`）
- `ProblemCatalog` 增 `normalizeNotes()`：空 trim → null，避免 DB 存空串
- 学生端 ProblemDetailView「说明」tab：有 `notes` 渲染 Markdown，无则显示 `notesEmpty` 文案

**Markdown 编辑器集成**：
- 新依赖：`md-editor-v3 ^5.8.5`（根 package.json，npm workspaces hoist 到 admin 和 user）
- 管理端 ProblemEditorDrawer：自研 7 按钮 toolbar + `<a-textarea>` 整体替换为 `<MdEditor>`（statement + notes 两个）
- 学生端 ProblemPane：`v-html="statementHtml"` 替换为 `<MdPreview>`，**编辑解析器和展示解析器一致**（杜绝 WYSIWYG drift）
- 题面与样例 tab 改竖向：statement 编辑器 → notes 编辑器 → 样例卡片，每个全宽

**新增 i18n keys**（中英同步）：`problems.notesLabel`、`problems.notesEditorPlaceholder`、`problems.notesHelper`、`problems.notesEmpty`

### 本次 admin 修复明细（commits `5c09069` + `1b1e89c`）

### 本次 admin 修复明细（commits `5c09069` + `1b1e89c`）

**Task A · 正确性与安全**：
- 已登录用户访问 `/register` 不再被强制清登录态（router 移除过激清理）
- 路由 `loadProfile` 加 60s 新鲜度窗口，结束「每切换路由打一次 `/me`」
- `BlockedView.goLogin` 改为先 `api.logout()` 再 replace，撤销服务端 refresh token
- 题目编辑器停止静默把第一个 case 强制改 sample=true
- `timeLimit / memoryLimit` 用 `??` 替 `||`，0 不再被悄悄改写
- 内存 KB↔MB 不再 round getter（避免编辑时悄悄改值）
- 测试包上传 200MB 客户端硬上限（防浏览器 OOM）
- 测试包后端处理超时改为抛 `t('testcase.pollTimeout')`，不再静默退出

**Task B · UX 与精度**：
- Dashboard 用 `Promise.allSettled` 容错，`enabledUsers` 改用专属 `?enabled=true` 总数请求（不再只统计前 20 条）
- Dashboard 加刷新按钮、修死代码 `>= 0`、`hasStats` 改 loaded 标志
- AiDrafts 从 3 个 API 减到 2 个，删除冗余 `loadCounts`
- TestcasePackage 上传成功后清空文件选择器；激活成功用专属 i18n key
- TestcasePackage 用 `watch({ immediate: true })` 替代 `onMounted + watch` 双触发
- 题库标签筛选 `@blur` + `@clear` 触发刷新
- `aioj:auth-expired` 监听器加 HMR 清理（`import.meta.hot.dispose`）

新增 i18n keys（中英同步）：`testcase.fileTooLarge`、`testcase.pollTimeout`、`testcase.activated`

## 8. 更新此文档

- 每次推送代码后，更新 **第 7 节**（贴最新 commit），并把已落地的待办从 §4 → §2。
- 发现新风险 → §5。
- 新依赖/版本/路由前缀 → 同步到 `CLAUDE.md`。
- 路线图阶段推进 → 更新 `docs/ROADMAP.md` 的状态标记。

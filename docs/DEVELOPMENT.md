# AI-OJ Next — Development Rules

> 完整开发习惯。`CLAUDE.md` 是"必须知道才能不破坏"的红线，本文件是"建议遵守以保证质量"的扩展。

---

## 1. 核心红线（违反即可能引发生产事故）

- 不要降 Java 版本，后端固定 JDK 17
- 浏览器只走 Gateway `/api/v1/...`，不要让前端直连 `:8201/:8202/:8203/:8204`
- 判题只通过 RabbitMQ 进入 `judge-worker`，**不要** HTTP 调 worker
- `judge-worker` 不直接执行用户代码，必须对接隔离沙箱
- Flyway 只能**新增** migration，不能改历史 `V*.sql`
- AI 出题必须 draft → approve → import，不能直接进题库
- 前端所有 16 位以上 ID 都按 `string` 处理，不能转 number（用 `preserveLargeIntegerIds`）

## 2. 代码组织

- 后端跨服务 DTO 放 `backend/api-contract`，不在各服务重复定义
- 通用响应 / 错误 / JWT / 安全过滤器 / TraceId 放 `common-lib`
- 前端请求**统一**走 `@aioj/api-client` 的 `api` 对象，页面组件不要直接 `fetch`
- 文案统一放 `packages/i18n/src/messages.ts`，不在组件内硬编码中英文
- 共享组件能下沉到 `packages/ui` 就不在 user/admin 重复写
- 样式分层：管理端偏**工具控制台**密度，学生端偏**学习体验**留白；蓝白卡片化主基调一致

## 3. 开发流程

- **先读真实文件，再动手**——不要凭印象改
- 小步提交：功能 / UI / 修 bug 尽量分开 commit
- 改完前端至少跑对应 workspace：
  - `npm run typecheck -w @aioj/web-user`
  - `npm run typecheck -w @aioj/web-admin`
  - `npm run build:user` / `npm run build:admin`
- 改 `@aioj/api-client` 或 `@aioj/i18n`：**user 和 admin 都要验**
- 改后端：至少跑相关模块 `mvn -pl <module> compile`；改 `api-contract` 后要编译所有依赖它的服务
- UI 改动尽量浏览器实际点一遍，不只看构建通过

## 4. Git 习惯

- 主分支：`master`；远端：`origin`
- 每次代码更新后及时 commit + push
- **不要**提交：
  - `graphify-out/`
  - `logs/`
  - `apps/*/dev-server*.log`
  - 构建产物、临时调试文件
  - `.env`、任何 secret 文件
- 提交信息用**动词开头**，简洁：
  - `fix admin register routing`
  - `refine problem editor drawer`
  - `add testcase package upload flow`
  - `refactor api client error handling`

## 5. 前端 UI 习惯

- 页面第一屏应该是**可用功能**，不做营销式落地页
- 所有列表必须有 `loading` / `empty` / `error` 三态
- 所有 AI / 提交 / 上传操作必须有等待态与失败态
- 不要让按钮、输入框、表格在移动端横向溢出
- 图标按钮尺寸、语言切换、用户菜单要保持全站统一
- 管理端 Drawer/Modal 避免长表单一滚到底——优先分 Tab、固定 Footer

## 6. 后端习惯

- Controller 统一返回 `ApiResponse<T>`
- 业务错误抛 `DomainException(ErrorCode, message)`；不要抛裸 `RuntimeException`
- 鉴权逻辑放过滤器 / 方法级权限（`@PreAuthorize`），不要在 controller 里到处手写
- Refresh token **只存 hash**，支持吊销和过期
- 发布 MQ 失败要记录可恢复状态，不要静默吞
- 大测试点**不要**塞 MySQL 文本字段——MySQL 只存元数据，文件走存储抽象（当前是 `LocalTestcaseStorageService`，未来可换 MinIO/S3）

## 7. 本地环境

- Windows 本地开发优先；Ubuntu 24 生产部署后续专项验收
- Nacos / Sentinel 可以保留，但本地调试主链路优先直连，别让注册发现阻塞开发
- 系统环境变量改完**重开终端 / Codex / IDE 进程**，否则当前进程读不到
- AI Key：当前约定兼容 `KIMI_API_KEY` 与代码实际读取的 `AI_API_KEY`，注意保持一致

## 8. 与 Claude / Codex 协作流程

- **设计**走 Claude（本会话）→ **执行**走 Codex
- 每个任务一份 `.txt` 提示词，放在 `docs/codex-prompts/`
- 提示词必须**自包含**（Codex 看不到对话）：包含 ROLE / CONTEXT / TASKS / CONSTRAINTS / VERIFICATION / OUTPUT FORMAT
- Codex 跑完先**不要 commit**——回到 Claude 这边复核 diff，确认后再提
- 修 bug 时遵循「先读真实文件 → 调研根因 → 写设计稿 → 输出提示词」四步，不要跳

## 9. 何时停下来问

下面这些情况**不要**自动决定，先确认：
- 改 `api-contract` 字段或路由前缀（影响所有依赖服务和前端）
- 改 Flyway（不可逆）
- 新增后端模块或服务（运维面变大）
- 改 JWT / 鉴权流程
- 引入新的第三方依赖
- 删除文件
- `git push --force` 或修改历史 commit

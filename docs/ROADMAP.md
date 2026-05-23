# AI-OJ Next — Roadmap

> 阶段化路线图。**重要原则**：基础底座未稳前，不开新功能分支。
>
> 状态标记：`[x]` 完成 · `[~]` 进行中 · `[ ]` 待启动 · `[!]` 阻塞

---

## Phase 0 · 稳固基础（当前优先）

目标：**让真实学生能在真实老师手里跑完一节课的练习闭环**。这阶段任何新功能（含用户组）必须暂停。

- [!] **真实隔离沙箱** — judge-worker 已搭好调用端，但沙箱本体（cgroup/namespace/seccomp/语言运行时镜像/输出截断/资源审计）未实现。这是 P0 阻塞项
- [ ] **大测试点全链路压测** — 50MB/100MB zip、断点续传、hash 校验、缺包/坏包死信、worker 缓存命中、`SYSTEM_ERROR` 审计完整性
- [ ] **Ubuntu 24 生产部署验收** — Docker secrets、Nginx HTTPS、Nacos/Sentinel 生产鉴权、Flyway 顺序、多实例启动顺序、外部备份恢复演练
- [ ] **关键 service 单测托底** — `JudgeTaskListener` / `TestcasePackageValidator` / `UserAccountService` / `JwtTokenService` 至少有正常路径 + 主要异常路径
- [ ] **AI 会话历史模型统一** — 后端 `AiConversationEntity` 增加 `mode/source/problemTitle/problemTags` 元数据；前端不再用 localStorage 元数据兜底
- [ ] **OpenAPI 自动同步 api-client** — `npm run gen:api` 从 `/v3/api-docs` 生成类型

完成判定：上述全部 `[x]`，并且能在 Ubuntu 上从 0 部署一遍通过 e2e 烟测（学生注册→做题→提交→评测→看结果）。

---

## Phase 1 · 用户组（Groups）基础

> 目标：**支持教师把学生组织成"班级/课程小组"**。不含题目集，只是用户的容器。

### 1.1 数据模型

```sql
-- backend/api-contract/src/main/resources/db/migration/V5__user_group.sql
CREATE TABLE user_group (
  id              BIGINT PRIMARY KEY,
  name            VARCHAR(120) NOT NULL,
  description     TEXT,
  owner_user_id   BIGINT NOT NULL,        -- 通常是教师
  type            VARCHAR(20) NOT NULL,   -- CLASS | COURSE | TEAM | OPEN
  join_policy     VARCHAR(20) NOT NULL,   -- INVITE_ONLY | CODE | OPEN
  invite_code     VARCHAR(32) UNIQUE,     -- join_policy = CODE 时使用
  enabled         TINYINT(1) NOT NULL DEFAULT 1,
  created_at      DATETIME(3) NOT NULL,
  updated_at      DATETIME(3) NOT NULL,
  INDEX idx_owner (owner_user_id),
  INDEX idx_code (invite_code)
);

CREATE TABLE user_group_member (
  id            BIGINT PRIMARY KEY,
  group_id      BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  role          VARCHAR(20) NOT NULL,    -- OWNER | TA | MEMBER
  status        VARCHAR(20) NOT NULL,    -- ACTIVE | REMOVED | PENDING
  joined_at     DATETIME(3) NOT NULL,
  removed_at    DATETIME(3),
  UNIQUE KEY uk_group_user (group_id, user_id),
  INDEX idx_user (user_id)
);
```

### 1.2 服务归属决策

**推荐**：归 `auth-service`（因为本质是用户的组织关系）。理由：
- 复用 `UserAccountService` 的角色与用户实体
- 避免新增 `group-service` 增加运维面
- 等并发/规模真的撑不住再独立成 `group-service :8205`

替代方案：新建 `group-service`，单独的库 schema。仅在 Phase 4 之后并发评估时考虑。

### 1.3 API（Gateway 路由前缀 `/api/v1/groups`）

- `POST /api/v1/groups` 创建（教师/管理员）
- `GET /api/v1/groups` 列表（筛 owner / type / 我加入的）
- `GET /api/v1/groups/{id}` 详情（含成员摘要）
- `PUT /api/v1/groups/{id}` 更新元数据
- `DELETE /api/v1/groups/{id}` 软禁
- `POST /api/v1/groups/{id}/members` 批量加成员（按 userId/account 列表）
- `DELETE /api/v1/groups/{id}/members/{userId}` 移除成员
- `POST /api/v1/groups/join` 学生用邀请码加入 `{ "code": "ABC123" }`
- `GET /api/v1/me/groups` 我加入的组

### 1.4 UI

**管理端**：
- 新增「用户组」一级导航 `/groups`
- 列表 + 创建抽屉 + 详情 Tab（基础信息 / 成员 / 历史）
- 成员管理：搜索用户、批量加入、移除、设 TA

**学生端**：
- 个人中心 + 一级导航「我的小组」`/my-groups`
- 「加入小组」入口（输入邀请码 / 浏览公开组）

### 1.5 i18n

新增 `groups.*` 命名空间，zh-CN 与 en-US 同步。

### 1.6 完成判定

- 教师在管理端建一个组，把 3 个学生加进去
- 学生在学生端能看到自己加入的组
- 学生能通过邀请码加入 OPEN 组
- 全链路 `npm run typecheck` + `mvn compile` 通过

---

## Phase 2 · 题目集（Problem Sets）+ 推送（Assignment）

> 目标：**教师把若干题打包成题集，分派给某用户组**。

### 2.1 数据模型

```sql
-- V6__problem_set.sql
CREATE TABLE problem_set (
  id              BIGINT PRIMARY KEY,
  title           VARCHAR(200) NOT NULL,
  description     TEXT,
  owner_user_id   BIGINT NOT NULL,
  visibility      VARCHAR(20) NOT NULL,   -- PRIVATE | GROUP | PUBLIC
  enabled         TINYINT(1) NOT NULL DEFAULT 1,
  created_at      DATETIME(3) NOT NULL,
  updated_at      DATETIME(3) NOT NULL
);

CREATE TABLE problem_set_item (
  id          BIGINT PRIMARY KEY,
  set_id      BIGINT NOT NULL,
  problem_id  BIGINT NOT NULL,
  sort_order  INT NOT NULL,
  score       INT NOT NULL DEFAULT 100,
  UNIQUE KEY uk_set_problem (set_id, problem_id),
  INDEX idx_set (set_id)
);

CREATE TABLE problem_set_assignment (
  id                  BIGINT PRIMARY KEY,
  set_id              BIGINT NOT NULL,
  group_id            BIGINT NOT NULL,
  starts_at           DATETIME(3),
  ends_at             DATETIME(3),
  allow_late          TINYINT(1) NOT NULL DEFAULT 1,
  score_policy        VARCHAR(20) NOT NULL,  -- BEST | LAST | AVERAGE
  created_at          DATETIME(3) NOT NULL,
  UNIQUE KEY uk_set_group (set_id, group_id)
);
```

### 2.2 服务归属

归 `problem-service`（与 `ProblemEntity` 同库，避免跨库事务）。

### 2.3 API（前缀 `/api/v1/problem-sets`、`/api/v1/me/assignments`）

- `POST /api/v1/problem-sets`（创建集合）
- `PUT /api/v1/problem-sets/{id}/items`（设题目顺序与分数）
- `POST /api/v1/problem-sets/{id}/assign` `{ groupId, startsAt, endsAt, allowLate, scorePolicy }`
- `GET /api/v1/me/assignments?status=ACTIVE|UPCOMING|FINISHED` 学生看到的任务
- `GET /api/v1/problem-sets/{id}/leaderboard?groupId=...` 教师看小组榜单

### 2.4 UI

**管理端**：
- 「题集」导航，列表 + 创建 + 编辑（拖拽题目排序 + 分数）
- 在题集详情里「分派到小组」按钮 → 选小组 + 时间窗 + 计分策略

**学生端**：
- Dashboard 顶部「正在进行的任务」卡片
- 题库页加筛选：来自哪个 assignment
- 题目详情页提示「这道题属于 XXX 任务，截止 YYY」

### 2.5 完成判定

- 教师创建题集 → 选 5 道题 → 分派到 1 个组（含截止时间）
- 学生看到任务卡片，点击进入正常做题流程
- 提交后服务端识别该提交属于 assignment，按 `scorePolicy` 计分
- 教师能看到小组榜单

---

## Phase 3 · 学生自报名 / 公开题集

> 目标：**支持公开题集（不绑定具体小组），学生自助报名**。

- [ ] `problem_set.visibility = PUBLIC` 时学生在「公开练习」列表可见
- [ ] `POST /api/v1/problem-sets/{id}/enroll`（学生自报名）
- [ ] `problem_set_enrollment(set_id, user_id, source[ASSIGNED|SELF|INVITED], joined_at)`
- [ ] 学生端 Dashboard 加「公开活动」入口
- [ ] 教师端能看到自报名用户列表（与分派的成员合并展示）

---

## Phase 4 · 计分与分析

- [ ] 学生端：任务结果页（每题状态 + 得分 + 截止前最后一次提交）
- [ ] 教师端：小组数据看板（完成率、平均分、最难题、提交时间分布）
- [ ] 导出 CSV（成绩册）
- [ ] AI 助教按小组的难点统计（哪些题学生最容易卡）

---

## Phase 5 · 收敛与优化（穿插进行）

- [ ] `packages/ui` 下沉：`PageHeader / EmptyState / StatusChip / DifficultyChip / BaseCard / ConfirmDialog`
- [ ] `api-client` 拆分：`types.ts` / `http.ts` / `endpoints/*.ts`
- [ ] Playwright e2e：登录 → 做题 → 提交 → 评测的最小闭环
- [ ] 性能：Submission 列表 + 大题集分页 + Dashboard 聚合查询的 N+1 检查

---

## 路线图变更须知

- 每个阶段开启前，先在 `docs/codex-prompts/` 落地一份**总览提示词**（架构 + 数据迁移 + API 契约 + 验收）
- 各子任务再拆出独立 `.txt` 提示词
- 阶段完成时，把状态标记从 `[ ]` 改为 `[x]`，并在 `docs/HANDOVER.md` §2 同步追加

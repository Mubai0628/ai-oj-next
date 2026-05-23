# Codex Prompts

> 设计走 Claude（在主对话里产出），执行走 Codex（在本地代码库里跑）。
> 这个目录是**所有给 Codex 的提示词的存档**——历史 + 待执行。

---

## 命名规范

```
NNN-short-description.txt        # 待执行（最新一条等 Codex 拿走）
NNN-short-description.applied.txt # 已执行（Codex 跑完，commit 已推后改后缀）
```

`NNN` 是 3 位序号，按时间递增（`001`, `002`, ...）。短描述用 kebab-case，5-7 词以内。

示例：
- `001-project-guide-and-ignores.applied.txt`
- `002-admin-session-and-register.applied.txt`
- `003-real-sandbox-skeleton.txt`

## 提示词的标准结构

每个 `.txt` 必须**自包含**（Codex 看不到本会话上下文）：

```
ROLE
  描述 Codex 是什么角色，在哪个仓库工作，什么风格

CONTEXT YOU NEED
  - 必读的文件路径
  - 已知的约束（不要碰哪些文件、不要装哪些依赖）

TASK 1 — <名称>
  逐步骤指令
  - 用绝对路径或仓库相对路径，不要含糊
  - 涉及代码片段直接给出，不要让 Codex 自己想

TASK 2 — <名称>
  ...

CONSTRAINTS
  - 一律禁止的行为列表（don't commit、don't push、don't modify X）

VERIFICATION
  1. 跑哪些命令
  2. 期望的 git status 输出（精确列表）
  3. 要展示的 diff / 文件

OUTPUT FORMAT
  - 分几个 section
  - 每个 section 显示什么

DO NOT
  - 不要 commit/push/install
```

## 工作流

1. **Claude 端**：用户提任务 → Claude 调研真实代码 → 设计修复方案 → 输出 `.txt` 提示词到本目录
2. **用户**：把 `.txt` 内容复制给 Codex（或让 Codex 直接读取此文件）
3. **Codex**：按提示词执行，**不 commit**，反馈结果给用户
4. **Claude 端**：用户回传结果 → Claude 复核 diff → 决定是否再修补 / 直接 commit
5. **commit 后**：把 `.txt` 后缀改为 `.applied.txt`，并在 `docs/HANDOVER.md` 同步更新

## 历史记录

已经被 Codex 执行的提示词（在 commit 记录里能找到对应改动）：

| 文件 | 关联 commit | 简述 |
|---|---|---|
| `001-project-guide-and-ignores.applied.txt` | `80a3d07` | 创建 CLAUDE.md，补 .gitignore |
| `002-admin-session-and-register.applied.txt` | `d27d906`, `2990d39` | 会话过期事件全局化、管理端注册改独立路由 |

> 注：早期提示词没有归档到这个目录，仅在 Claude 会话历史中。从现在起所有新提示词都会以 `.txt` 落地存档。

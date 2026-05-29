# Task: 全仓 a-alert 用法规范化（修复「红框无文字」+ 写进 CLAUDE.md）
## Status: done
## Created by: Claude @ 2026-05-25T22:00+08:00
## Linked: outbox/2026-05-25-2200-alert-content-slot-normalize.md

---

## Prompt

ROLE
You are Codex executing a project-wide convention normalization task
designed by Claude. Read this entire file before starting. When done,
write the report to
`docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md`
per `docs/codex-exchange/README.md` template, and update this file's
`## Status: new` → `## Status: done`.

CONTEXT — root cause already identified
- 截图证据：管理端上传 zip 测试包失败时，红色 `<a-alert>` 出现但里面没有文字。
- 调研结论（Claude 已确认）：
  - 后端正常返回 `{ code: 40000, message: "Testcase package manifest.json is required" }`
  - `ApiError.userMessage` 三层兜底 (`resolved || serverMessage || "Unknown error"`) 永远非空
  - web-admin 的 `messageResolver` 注册逻辑正确，且 `errors.40000 = "请求参数有误。"` 在 i18n 已注册
  - **问题在前端模板写法**：`<a-alert :content="X" />`（self-closing + content prop）在 Arco 2.58 / Vue 3.5 组合下渲染失败，框出现但文字消失
  - 对比证据：`apps/web-admin/src/views/LoginView.vue` 用 default slot 写法 `<a-alert>{{ error }}</a-alert>`，从未出过这个问题
  - 全仓 grep 显示 web-admin 有 8 处 `<a-alert ... :content="X" />` 用法是 self-closing prop 写法，全部需要改

修复策略：把所有 `<a-alert :content="X" />` 改为 `<a-alert>{{ X }}</a-alert>`
default slot 写法。这是 Arco 官方文档推荐写法，跨版本最稳。

CONSTRAINTS (hard)
- 只改 frontend `.vue` 文件。No backend, no api-client, no migrations.
- **只动 `<a-alert>` 元素上的 `:content` prop**。
  其他组件（`<a-popconfirm>`, `<a-tooltip>`, `<a-tag>` 等）的 `:content`
  prop 是稳定的，**绝对不要动**。
- 保留所有 alert 上的其他 props：`type` / `show-icon` / `closable` /
  `@close` / `class` / `v-if` 等原样保留。
- 不引入新 i18n key。
- 不改 ApiError 实现、不改 messageResolver、不改 errors.* 映射。
- 不 commit, stage, push.

============================================================
TASK 1 — Audit: enumerate every `<a-alert :content="...">` usage
============================================================

Use grep / file search to find ALL occurrences of `<a-alert` that use
`:content="..."` as a prop (either self-closing `/>` or with closing
tag but empty body). Expected hits (based on Claude's grep — verify
and add anything missed):

- `apps/web-admin/src/components/TestcasePackageUploader.vue` (lines 11, 12)
- `apps/web-admin/src/views/AiDraftsView.vue` (lines 22, 98)
- `apps/web-admin/src/views/DashboardView.vue` (line 7)
- `apps/web-admin/src/views/UsersView.vue` (line 24)
- `apps/web-admin/src/views/RolesView.vue` (line 3)
- `apps/web-admin/src/views/RegisterView.vue` (around line 17)
- `apps/web-admin/src/views/ProblemsView.vue` (line 28)

If any new hit is found that Claude did not list, INCLUDE it in the
fix and call it out in the report.

In the report, list every fixed location as
`{file}:{line} — {before} → {after}` so the reviewer can scan it.

============================================================
TASK 2 — Apply the rewrite
============================================================

For EACH hit found in TASK 1, transform:

```vue
<a-alert v-if="error" type="error" show-icon class="form-alert" :content="error" />
```

into:

```vue
<a-alert v-if="error" type="error" show-icon class="form-alert">
  {{ error }}
</a-alert>
```

Rules:
- Keep every existing attribute / directive EXCEPT remove `:content="X"`
- Replace self-closing `/>` with `></a-alert>` close tag, and place
  `{{ X }}` (or `t(key)` if the original prop was an i18n call) as the
  default slot content
- If `X` was `t('some.key')`, the slot becomes `{{ t('some.key') }}`
- If `X` was a complex expression, preserve it inside the slot
- Maintain indentation matching surrounding code

Example for an i18n call:

```vue
<!-- before -->
<a-alert v-if="!problemId" type="warning" show-icon class="form-alert" :content="t('testcase.noProblem')" />

<!-- after -->
<a-alert v-if="!problemId" type="warning" show-icon class="form-alert">
  {{ t('testcase.noProblem') }}
</a-alert>
```

============================================================
TASK 3 — Add a project convention to CLAUDE.md §5.8 (前端约定)
============================================================

In `CLAUDE.md` section §5.8 "前端约定", APPEND one new bullet point
to the existing bullet list (right before the last bullet about i18n
keys, OR at the end — choose wherever it fits cleanly):

```
- `<a-alert>` 必须用 **default slot** 渲染文本（`<a-alert>{{ message }}</a-alert>`），
  **不要**用 `:content="..."` prop 写自闭合 `<a-alert ... />`——Arco 2.58 + Vue 3.5
  组合下 self-closing + `:content` 会出现「红框无文字」渲染异常。`<a-popconfirm>`
  / `<a-tooltip>` 的 `:content` 不受影响，保持原写法。
```

If §5.8 doesn't already have a bulleted list (it does), preserve the
list style. Do not reorder existing bullets.

============================================================
TASK 4 — Update docs/HANDOVER.md §5 known risks
============================================================

In `docs/HANDOVER.md` §5 "已知风险与坑", APPEND one new bullet at the
end:

```
- **Arco a-alert `:content` prop 渲染异常**：self-closing `<a-alert ... />`
  + `:content="X"` 在 Arco 2.58 + Vue 3.5 下会渲染「红框无文字」。规范见
  CLAUDE.md §5.8；全仓在 2026-05-25 已统一为 default slot 写法。
```

============================================================
TASK 5 — typecheck
============================================================

Run both:
- `npm run typecheck -w @aioj/web-admin` (the main impact area)
- `npm run typecheck -w @aioj/web-user` (regression safety; should be
  unchanged but verify)

Both must exit 0. Show last 5 lines of each.

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED (in any order, list NEW additions
   for this task; baseline doc dirty files may remain from prior tasks
   and are tolerated, but call them out if anything unexpected appears):
     M  apps/web-admin/src/components/TestcasePackageUploader.vue
     M  apps/web-admin/src/views/AiDraftsView.vue
     M  apps/web-admin/src/views/DashboardView.vue
     M  apps/web-admin/src/views/UsersView.vue
     M  apps/web-admin/src/views/RolesView.vue
     M  apps/web-admin/src/views/RegisterView.vue
     M  apps/web-admin/src/views/ProblemsView.vue
     M  CLAUDE.md
     M  docs/HANDOVER.md
     M  docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
     ?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
   If any other source file is modified, REVERT it.

2. For each changed .vue file, focused diff (~10 lines context, can be
   tight since changes are small per location).

3. For CLAUDE.md and docs/HANDOVER.md: full diff of the appended
   section (~10 lines context).

4. typecheck output (last 5 lines each).

5. Audit table format in the report (Section "Audit summary"):

   | file | line | before | after |
   |---|---|---|---|
   | apps/web-admin/src/.../X.vue | N | `<a-alert :content="Y" />` | `<a-alert>{{ Y }}</a-alert>` |
   | ... | ... | ... | ... |

   This is the most important deliverable for the human reviewer.

6. Manual verification checklist (for the human to run later):
   [ ] Open admin → 题库 → 编辑题目 → 测试包 tab → upload an obviously
       invalid zip (e.g. empty zip without manifest.json) → red alert
       appears AND shows actual text (e.g. "请求参数有误。" or
       "Testcase package manifest.json is required")
   [ ] Same admin upload, try with `problemId` empty → yellow warning
       alert shows text "请先选择题目" (or whatever testcase.noProblem
       resolves to in i18n)
   [ ] Admin login: try wrong password → red alert shows
       "账户或密码错误" text (LoginView already uses default slot, so
       this is a regression check)
   [ ] Admin /dashboard load error simulation (turn off backend) →
       red alert renders error text
   [ ] zh ↔ en language switch — all converted alerts still render
       text correctly
   [ ] `<a-popconfirm>` and `<a-tooltip>` :content unchanged and still
       work (this verifies you did NOT over-rewrite)

7. 5-sentence Chinese summary covering:
   - 真根因（Arco self-closing :content 渲染异常）
   - 改了多少处，分布在哪些文件
   - 为什么规范成 default slot 是稳定选择
   - CLAUDE.md / HANDOVER.md 已写入规范，防再次踩坑
   - popconfirm / tooltip 未动作的判定理由

OUTPUT
Write all sections into:
  `docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md`
Then update this inbox `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- 7+ `.vue` files under `apps/web-admin/src/` (see TASK 1 list)
- `CLAUDE.md`
- `docs/HANDOVER.md`
- This inbox file (Status only)
- The outbox file (new)

**Read for reference (do not modify)**:
- `apps/web-admin/src/views/LoginView.vue` — gold standard default-slot
  usage; mirror its pattern
- `apps/web-user/src/views/**/*.vue` — already use default slot
  consistently; do not modify, just confirm via grep these are NOT
  using `:content` on alerts

**Hard 禁区**:
- backend/**
- packages/api-client/**
- packages/i18n/** (do not add/remove i18n keys)
- apps/web-user/** source files (regression-only; verify no change in
  git status)
- Anything not listed above
- Do not touch `:content` on `<a-popconfirm>`, `<a-tooltip>`, or any
  non-alert component

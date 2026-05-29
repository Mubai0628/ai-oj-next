# Task: 全仓 a-alert 用法规范化（修复「红框无文字」+ 写进 CLAUDE.md）
## Status: done
## Created by: Codex @ 2026-05-26T09:35+08:00
## Linked: inbox/2026-05-25-2200-alert-content-slot-normalize.md

---

## Report

### Section 1: git status --short

```text
 M CLAUDE.md
 M apps/web-admin/src/components/TestcasePackageUploader.vue
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
 M apps/web-user/src/views/SubmissionsView.vue
 M docs/HANDOVER.md
 M docs/deployment.md
 M packages/i18n/src/messages.ts
?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
?? docs/codex-exchange/README.md
?? docs/codex-exchange/inbox/.gitkeep
?? docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
```

本任务新增/修改：
- `apps/web-admin/src/components/TestcasePackageUploader.vue`
- `apps/web-admin/src/views/AiDraftsView.vue`
- `apps/web-admin/src/views/DashboardView.vue`
- `apps/web-admin/src/views/ProblemsView.vue`
- `apps/web-admin/src/views/RegisterView.vue`
- `apps/web-admin/src/views/RolesView.vue`
- `apps/web-admin/src/views/UsersView.vue`
- `CLAUDE.md`
- `docs/HANDOVER.md`
- `docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md`
- `docs/codex-exchange/outbox/2026-05-25-2200-alert-content-slot-normalize.md`

其余条目为进入本任务前已存在的 baseline dirty files，已保留未回滚。

### Section 2: Audit summary

| file | line | before | after |
|---|---:|---|---|
| `apps/web-admin/src/components/TestcasePackageUploader.vue` | 11 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/components/TestcasePackageUploader.vue` | 12 | `<a-alert ... :content="t('testcase.noProblem')" />` | `<a-alert ...>{{ t('testcase.noProblem') }}</a-alert>` |
| `apps/web-admin/src/views/AiDraftsView.vue` | 22 | `<a-alert ... :content="generateError" />` | `<a-alert ...>{{ generateError }}</a-alert>` |
| `apps/web-admin/src/views/AiDraftsView.vue` | 98 | `<a-alert ... :content="listError" />` | `<a-alert ...>{{ listError }}</a-alert>` |
| `apps/web-admin/src/views/DashboardView.vue` | 7 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/views/ProblemsView.vue` | 28 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/views/RolesView.vue` | 3 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/views/RegisterView.vue` | 17 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/views/UsersView.vue` | 24 | `<a-alert ... :content="error" />` | `<a-alert ...>{{ error }}</a-alert>` |
| `apps/web-admin/src/views/UsersView.vue` | 67 | `<a-alert ... :content="roleWarning" />` | `<a-alert ...>{{ roleWarning }}</a-alert>` |

`rg -n -g "*.vue" -- "<a-alert.*:content=" apps/web-admin/src apps/web-user/src` 复查无命中。`rg -n -g "*.vue" -- ":content=" ...` 仅剩 `a-popconfirm` / `a-tooltip`，按要求未改。

### Section 3: focused diffs for Vue files

```diff
diff --git a/apps/web-admin/src/components/TestcasePackageUploader.vue b/apps/web-admin/src/components/TestcasePackageUploader.vue
@@
-    <a-alert v-if="error" type="error" show-icon class="form-alert" :content="error" />
-    <a-alert v-if="!problemId" type="warning" show-icon class="form-alert" :content="t('testcase.noProblem')" />
+    <a-alert v-if="error" type="error" show-icon class="form-alert">
+      {{ error }}
+    </a-alert>
+    <a-alert v-if="!problemId" type="warning" show-icon class="form-alert">
+      {{ t('testcase.noProblem') }}
+    </a-alert>
diff --git a/apps/web-admin/src/views/AiDraftsView.vue b/apps/web-admin/src/views/AiDraftsView.vue
@@
-      <a-alert v-if="generateError" type="error" show-icon class="form-alert" :content="generateError" />
+      <a-alert v-if="generateError" type="error" show-icon class="form-alert">
+        {{ generateError }}
+      </a-alert>
@@
-      <a-alert v-if="listError" type="error" show-icon :content="listError" />
+      <a-alert v-if="listError" type="error" show-icon>
+        {{ listError }}
+      </a-alert>
diff --git a/apps/web-admin/src/views/DashboardView.vue b/apps/web-admin/src/views/DashboardView.vue
@@
-    <a-alert v-if="error" type="error" show-icon :content="error" />
+    <a-alert v-if="error" type="error" show-icon>
+      {{ error }}
+    </a-alert>
diff --git a/apps/web-admin/src/views/ProblemsView.vue b/apps/web-admin/src/views/ProblemsView.vue
@@
-    <a-alert v-if="error" type="error" show-icon :content="error" />
+    <a-alert v-if="error" type="error" show-icon>
+      {{ error }}
+    </a-alert>
diff --git a/apps/web-admin/src/views/RegisterView.vue b/apps/web-admin/src/views/RegisterView.vue
@@
-          :content="error"
-        />
+        >
+          {{ error }}
+        </a-alert>
diff --git a/apps/web-admin/src/views/RolesView.vue b/apps/web-admin/src/views/RolesView.vue
@@
-    <a-alert v-if="error" type="error" show-icon :content="error" />
+    <a-alert v-if="error" type="error" show-icon>
+      {{ error }}
+    </a-alert>
diff --git a/apps/web-admin/src/views/UsersView.vue b/apps/web-admin/src/views/UsersView.vue
@@
-    <a-alert v-if="error" type="error" show-icon :content="error" />
+    <a-alert v-if="error" type="error" show-icon>
+      {{ error }}
+    </a-alert>
@@
-          :content="roleWarning"
-        />
+        >
+          {{ roleWarning }}
+        </a-alert>
```

### Section 4: focused docs diffs

```diff
diff --git a/CLAUDE.md b/CLAUDE.md
@@
 - 共享 UI 组件下沉到 `@aioj/ui`，**不**在 user/admin 各自重写一遍。
 - 全局样式分层：`tokens.css`（设计令牌） → `global.css`（基础） → `layout.css`（布局） → 组件 scoped 样式。
+- `<a-alert>` 必须用 **default slot** 渲染文本（`<a-alert>{{ message }}</a-alert>`），
+  **不要**用 `:content="..."` prop 写自闭合 `<a-alert ... />`——Arco 2.58 + Vue 3.5
+  组合下 self-closing + `:content` 会出现「红框无文字」渲染异常。`<a-popconfirm>`
+  / `<a-tooltip>` 的 `:content` 不受影响，保持原写法。
 - i18n 文案放 `@aioj/i18n/src/messages.ts`，禁止硬编码中文/英文到组件。
diff --git a/docs/HANDOVER.md b/docs/HANDOVER.md
@@
 - **Flyway 半成功状态**：MySQL DDL 不在事务里，一条 V*.sql 多 DDL 时
   前面成功后面失败会留下"半完成 schema + flyway success=0"。处置 SOP
   见 docs/deployment.md «Flyway 迁移失败的恢复 SOP»。
+- **Arco a-alert `:content` prop 渲染异常**：self-closing `<a-alert ... />`
+  + `:content="X"` 在 Arco 2.58 + Vue 3.5 下会渲染「红框无文字」。规范见
+  CLAUDE.md §5.8；全仓在 2026-05-25 已统一为 default slot 写法。
```

注：`CLAUDE.md` 和 `docs/HANDOVER.md` 还有进入本任务前的 baseline diff；上方仅列本任务新增片段。

### Section 5: typecheck output

`npm run typecheck -w @aioj/web-admin` last 5 lines:

```text
Active code page: 65001

> @aioj/web-admin@0.1.0 typecheck
> vue-tsc --noEmit
```

`npm run typecheck -w @aioj/web-user` last 5 lines:

```text
Active code page: 65001

> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 6: manual verification checklist

```text
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
```

### Section 7: Chinese summary

真根因是 Arco 2.58 + Vue 3.5 下 self-closing `<a-alert ... />` 配合 `:content` prop 会出现 alert 外框渲染但文本消失。已统一修改 10 处 `a-alert`，分布在测试包上传、AI 草稿、Dashboard、题目、角色、注册、用户管理 7 个 web-admin 文件。改为 default slot 后文本由 Vue 插槽稳定渲染，保留所有原有 `v-if`、`type`、`show-icon`、`class` 等 alert 行为。`CLAUDE.md` 和 `docs/HANDOVER.md` 已写入规范和风险说明，避免后续继续使用不稳定写法。`a-popconfirm` 和 `a-tooltip` 的 `:content` 是组件正常 API，不涉及本次 `a-alert` 渲染异常，因此按要求完全未改。

## Next-action hint

- 建议下一轮人工重点复现测试包上传失败场景，因为这是最直接验证“红框无文字”问题已修复的路径。

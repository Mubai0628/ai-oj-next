# Task: 「我的提交」行内化 + 查看按钮做成小矩形右贴
## Status: done
## Created by: Codex @ 2026-05-25T21:15+08:00
## Linked: inbox/2026-05-25-2100-submissions-row-layout.md

---

## Report

### Section 1: git status --short

```text
 M CLAUDE.md
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
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
```

本任务文件：
- `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`
- `docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md`
- `docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md`

既有 baseline 脏文件保留未回滚：
- `CLAUDE.md`
- `apps/web-user/src/components/problem/ProblemPane.vue`
- `apps/web-user/src/components/problem/ProblemTabs.vue`
- `apps/web-user/src/main.ts`
- `apps/web-user/src/styles.css`
- `apps/web-user/src/types/problem-workspace.ts`
- `apps/web-user/src/views/ProblemDetailView.vue`
- `apps/web-user/src/views/SubmissionsView.vue`
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue`
- `docs/HANDOVER.md`
- `docs/deployment.md`
- `packages/i18n/src/messages.ts`
- `docs/codex-exchange/README.md`
- `docs/codex-exchange/inbox/.gitkeep`
- `docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md`
- `docs/codex-exchange/outbox/.gitkeep`
- `docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md`

### Section 2: focused diff for ProblemSubmissionsPanel.vue

```diff
@@
       <div v-else class="problem-submission-list">
         <article v-for="item in records" :key="item.id" class="problem-submission-row">
-          <div class="problem-submission-row__main">
-            <StatusChip :label="statusLabel(item.status)" :tone="statusTone(item.status)" />
-            <span class="problem-submission-row__language">{{ item.language || '-' }}</span>
-          </div>
-          <div class="problem-submission-row__meta">
-            <span>{{ item.timeMillis ?? '-' }} ms</span>
-            <span>{{ formatMemory(item.memoryKb) }}</span>
-            <time>{{ formatRelative(item.createdAt) }}</time>
-          </div>
-          <a-button size="mini" type="text" @click="$emit('view', item.id)">
+          <StatusChip
+            class="problem-submission-row__chip"
+            :label="statusLabel(item.status)"
+            :tone="statusTone(item.status)"
+          />
+          <span class="problem-submission-row__language">{{ item.language || '-' }}</span>
+          <span class="problem-submission-row__metric">{{ item.timeMillis ?? '-' }} ms</span>
+          <span class="problem-submission-row__metric">{{ formatMemory(item.memoryKb) }}</span>
+          <time class="problem-submission-row__when">{{ formatRelative(item.createdAt) }}</time>
+          <a-button
+            class="problem-submission-row__view"
+            size="mini"
+            type="outline"
+            @click="$emit('view', item.id)"
+          >
             {{ t('problems.submissionsView') }}
           </a-button>
         </article>
@@
 .problem-submission-list {
   display: grid;
-  gap: 10px;
+  gap: 4px;
 }
 
 .problem-submission-row {
-  display: grid;
-  grid-template-columns: minmax(0, 1fr) auto;
-  gap: 8px 12px;
+  display: flex;
   align-items: center;
-  padding: 12px 0;
+  flex-wrap: wrap;
+  gap: 6px 10px;
+  padding: 10px 0;
   border-bottom: 1px solid var(--color-border-soft);
 }
@@
-.problem-submission-row__main {
-  min-width: 0;
-  display: flex;
-  flex-wrap: wrap;
-  align-items: center;
-  gap: 8px;
-}
-
 .problem-submission-row__language {
   color: var(--color-text-secondary);
   font-size: 13px;
   font-weight: 700;
 }
 
-.problem-submission-row__meta {
-  grid-column: 1 / -1;
-  display: flex;
-  flex-wrap: wrap;
-  gap: 8px 12px;
+.problem-submission-row__metric,
+.problem-submission-row__when {
   color: var(--color-text-muted);
   font-size: 12px;
+  white-space: nowrap;
+}
+
+.problem-submission-row__view {
+  margin-left: auto;
 }
 
-@media (max-width: 560px) {
-  .problem-submissions-panel__header,
+/* Narrow card: row may wrap onto multiple lines, but flex auto-margin
+   keeps the button right-aligned on whichever line it lands. */
+@media (max-width: 420px) {
   .problem-submission-row {
-    align-items: stretch;
-    grid-template-columns: 1fr;
+    row-gap: 4px;
   }
 }
```

### Section 3: typecheck last 10 lines

```text
Active code page: 65001

> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 4: manual verification checklist

```text
[ ] Open `/problems/{some id}` → click "我的提交" tab
[ ] Each submission renders on ONE flex line at normal panel width:
    [chip] [language] [ms] [MB] [relative-time]               [查看]
[ ] The "查看" button is a small bordered rectangle, NOT a text link
[ ] Narrow the browser to ~360px → the row wraps; the "查看" button
    is still right-aligned on whichever line it ends up
[ ] Click "查看" → existing detail modal opens (centered, with
    backdrop) — no regression
[ ] `/submissions` page is unchanged (this task only edits the
    problem-page panel)
[ ] zh ↔ en language switch still labels every column correctly
```

### Section 5: Chinese summary

本次只调整了“我的提交”面板的单行记录结构，把状态、语言、耗时、内存、相对时间和查看按钮放到同一个 flex row 中，删除原来的两层信息 wrapper。查看按钮使用 Arco 支持的 `type="outline"`，类型检查通过，因此保留为小矩形描边按钮而没有降级到 `secondary`。窄屏下行内容允许换行，按钮通过 `margin-left: auto` 继续贴在其所在行的右侧，原有刷新、加载、错误、空状态、查看事件和详情弹窗行为不变。

## Next-action hint

- 人工浏览器验收时重点看左侧卡片实际宽度下是否真的“一行可读”，如果真实题目标题区导致左栏更窄，可再微调 metric 间距或隐藏部分次要指标。

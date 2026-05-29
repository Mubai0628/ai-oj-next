# Task: 详情弹窗去除重复的「复制代码」按钮
## Status: done
## Created by: Codex @ 2026-05-26T09:20+08:00
## Linked: inbox/2026-05-25-2200-submission-modal-dedup-copy.md

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
?? docs/codex-exchange/inbox/2026-05-25-2200-alert-content-slot-normalize.md
?? docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
```

本任务实际触碰：
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue`
- `docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md`
- `docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md`

说明：`SubmissionDetailModal.vue` 和 inbox 文件在当前工作区仍是上一轮未提交的 untracked baseline，因此 `git diff -- apps/web-user/src/components/submission/SubmissionDetailModal.vue` 没有输出。已保留其他既有 dirty/baseline 文件，未做回滚。

### Section 2: full diff for SubmissionDetailModal.vue

```diff
@@
-        <div class="submission-detail-code-head">
-          <strong>{{ t('submissions.viewCodeTitle') }}</strong>
-          <a-button size="small" :disabled="!detail.code" @click="copyDetailCode">
-            {{ t('submissions.viewCodeCopy') }}
-          </a-button>
-        </div>
         <div class="submission-detail-code-body">
           <MdPreview
             v-if="detail.code"
@@
 <script setup lang="ts">
 import { computed, ref, watch } from 'vue';
 import { useI18n } from 'vue-i18n';
-import { Message } from '@arco-design/web-vue';
 import { ApiError, api, type EntityId, type SubmissionResponse } from '@aioj/api-client';
 import { MdPreview } from 'md-editor-v3';
 import 'md-editor-v3/lib/preview.css';
@@
-async function copyDetailCode() {
-  if (!detail.value?.code) return;
-  try {
-    await navigator.clipboard.writeText(detail.value.code);
-    Message.success(t('submissions.viewCodeCopied'));
-  } catch {
-    Message.success(t('submissions.viewCodeCopied'));
-  }
-}
-
 watch(
```

### Section 3: typecheck last 10 lines

```text
Active code page: 65001

> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 4: manual verification checklist

```text
[ ] Open `/submissions` → click "查看" → modal shows ONE copy button
    (top-right of the code block from MdPreview), no second button
    next to "提交代码详情" title (the title row is gone too)
[ ] Open `/problems/{id}` → "我的提交" tab → click "查看" → same
    result: only the MdPreview built-in copy button
[ ] Click that copy button → code is copied to clipboard (MdPreview
    shows its own success indicator)
[ ] Empty-code submission (rare) → still shows `<a-empty>` fallback
    under the code area
[ ] Switching zh ↔ en still works (no broken i18n key)
```

### Section 5: Chinese summary

已从共享 `SubmissionDetailModal.vue` 中移除 body 内重复的标题行和手工「复制代码」按钮，同时删除了对应的 `copyDetailCode` 函数。保留下来的复制入口是 `MdPreview` 代码块自带的复制按钮，代码预览仍包在 `submission-detail-code-body` 容器中，空代码场景仍显示 `<a-empty>`。`Message` import 已移除，因为该文件里不再需要手动 toast。

## Next-action hint

- 建议人工分别从 `/submissions` 和做题页「我的提交」入口打开弹窗，确认只剩代码块右上角的 MdPreview 内置复制按钮。

# Task: 详情弹窗去除重复的「复制代码」按钮
## Status: done
## Created by: Claude @ 2026-05-25T22:00+08:00
## Linked: outbox/2026-05-25-2200-submission-modal-dedup-copy.md

---

## Prompt

ROLE
You are Codex executing a small UI cleanup task designed by Claude.
Read this entire file. When done, write the report to
`docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md`
following `docs/codex-exchange/README.md` template. Update this file's
top `## Status: new` → `## Status: done` after finishing.

CONTEXT
The shared `SubmissionDetailModal.vue` is used by BOTH
`apps/web-user/src/views/SubmissionsView.vue` and the problem detail
page's "我的提交" tab. Inside that modal, there are currently TWO
"copy code" buttons:

1. A manual `<a-button>{{ t('submissions.viewCodeCopy') }}</a-button>`
   in the `submission-detail-code-head` row (next to the strong title)
2. The native one rendered by the `<MdPreview>` component itself in the
   top-right of every code block (md-editor-v3 built-in)

They do the same thing. Keep only the MdPreview built-in one. Remove
the manual one along with its unused logic.

CONSTRAINTS (hard)
- Edit ONLY `apps/web-user/src/components/submission/SubmissionDetailModal.vue`.
- Do not touch `SubmissionsView.vue`, `ProblemSubmissionsPanel.vue`,
  `ProblemDetailView.vue`, or any backend/api-client/i18n file.
- Do not introduce or remove i18n keys (the key
  `submissions.viewCodeCopy` may still be referenced from
  `submissions.viewCodeCopied` toast; only remove it from this file's
  template/script).
- Do not commit, stage, push.

============================================================
TASK 1 — Remove the manual copy button block
============================================================

In `apps/web-user/src/components/submission/SubmissionDetailModal.vue`:

REMOVE the entire `<div class="submission-detail-code-head">…</div>`
block from the template:

```vue
<div class="submission-detail-code-head">
  <strong>{{ t('submissions.viewCodeTitle') }}</strong>
  <a-button size="small" :disabled="!detail.code" @click="copyDetailCode">
    {{ t('submissions.viewCodeCopy') }}
  </a-button>
</div>
```

The modal already has a title (the modal header). The strong text
"提交代码详情" inside the body is redundant with that header. So drop
the whole row, not just the button.

In `<script setup>`, REMOVE:
- the entire `async function copyDetailCode() { ... }` function
- the `import { Message } from '@arco-design/web-vue';` if `Message` is
  not referenced anywhere else in this file (check first; if it IS
  still referenced, leave the import alone)

============================================================
TASK 2 — Make MdPreview the only copy affordance
============================================================

The remaining `<MdPreview>` element renders md-editor-v3's built-in
copy-button in the top-right of every code block — that satisfies the
"users can copy" requirement. Verify it is still wrapped by
`<div class="submission-detail-code-body">` so the styles continue to
apply. No CSS changes needed.

============================================================
TASK 3 — typecheck
============================================================

Run `npm run typecheck -w @aioj/web-user`. Must exit 0. Show last
10 lines in the report.

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  apps/web-user/src/components/submission/SubmissionDetailModal.vue
     M  docs/codex-exchange/inbox/2026-05-25-2200-submission-modal-dedup-copy.md
     ?? docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md
   Any other entry MUST be reverted.

2. Full diff for SubmissionDetailModal.vue.

3. Last 10 lines of typecheck.

4. Manual verification checklist (for the human):
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

5. 3-sentence Chinese summary of: what was removed, what remains, and
   whether `Message` import was kept.

OUTPUT
Write all sections into:
  `docs/codex-exchange/outbox/2026-05-25-2200-submission-modal-dedup-copy.md`
Then update this inbox's `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue`
- This inbox file (Status only)
- The outbox file (new)

**Hard 禁区**:
- All other .vue files
- backend/**
- packages/**
- any V*.sql

# Task: 「我的提交」行内化 + 查看按钮做成小矩形右贴
## Status: done
## Created by: Claude @ 2026-05-25T21:00+08:00
## Linked: outbox/2026-05-25-2100-submissions-row-layout.md

---

## Prompt

ROLE
You are Codex executing a small follow-up UI tweak designed by Claude.
Read this entire file before starting. When done, write the full
report to `docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md`
using the section structure defined in `docs/codex-exchange/README.md`.
Then update THIS file's top `## Status: new` to `## Status: done`
(or `blocked` with reason).

CONTEXT
The previous task (inbox/2026-05-25-1930-problem-detail-history-tab.md,
Status: done) shipped the "My submissions" panel inside the left card
on the problem detail page. Current row layout is wasteful in vertical
space:

    Line 1:  [status chip]  [language]
    Line 2:  12 ms   1 MB   1 小时前
    Line 3:  ........查看 (centered, text-style link)

The user wants ONE row per submission, with the "view" action rendered
as a small RECTANGULAR (bordered) button anchored to the right edge.

CONSTRAINTS (hard)
- Edit ONLY `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`.
- Do NOT touch `SubmissionDetailModal`, `SubmissionsView.vue`,
  `ProblemPane.vue`, `messages.ts`, styles.css, or any backend/API
  files. Behavior of `view` emission, refresh, loading, error, empty
  state, watch-on-problemId-change MUST remain identical.
- Do NOT introduce new i18n keys. Reuse `problems.submissionsView`.
- Do NOT commit, stage, push.

============================================================
TASK 1 — Restructure the per-row template
============================================================

In ProblemSubmissionsPanel.vue, REPLACE the existing per-row block:

```vue
<article v-for="item in records" :key="item.id" class="problem-submission-row">
  <div class="problem-submission-row__main">
    <StatusChip :label="statusLabel(item.status)" :tone="statusTone(item.status)" />
    <span class="problem-submission-row__language">{{ item.language || '-' }}</span>
  </div>
  <div class="problem-submission-row__meta">
    <span>{{ item.timeMillis ?? '-' }} ms</span>
    <span>{{ formatMemory(item.memoryKb) }}</span>
    <time>{{ formatRelative(item.createdAt) }}</time>
  </div>
  <a-button size="mini" type="text" @click="$emit('view', item.id)">
    {{ t('problems.submissionsView') }}
  </a-button>
</article>
```

WITH a flat one-line structure (no inner wrappers), where the view
button is the LAST flex child:

```vue
<article v-for="item in records" :key="item.id" class="problem-submission-row">
  <StatusChip
    class="problem-submission-row__chip"
    :label="statusLabel(item.status)"
    :tone="statusTone(item.status)"
  />
  <span class="problem-submission-row__language">{{ item.language || '-' }}</span>
  <span class="problem-submission-row__metric">{{ item.timeMillis ?? '-' }} ms</span>
  <span class="problem-submission-row__metric">{{ formatMemory(item.memoryKb) }}</span>
  <time class="problem-submission-row__when">{{ formatRelative(item.createdAt) }}</time>
  <a-button
    class="problem-submission-row__view"
    size="mini"
    type="outline"
    @click="$emit('view', item.id)"
  >
    {{ t('problems.submissionsView') }}
  </a-button>
</article>
```

Notes:
- `type="outline"` gives a thin-bordered rectangular button in the
  Arco 2.58 theme — that matches the user's "小矩形" request. If your
  in-repo design tokens make `outline` invisible (rare), fall back to
  `type="secondary"`; document the choice in the Chinese summary.
- Keep `size="mini"` so the button height matches a compact row.
- Do NOT change `statusLabel`, `statusTone`, `formatMemory`,
  `formatRelative`, `loadList`, the watch, the emit signature, or
  imports related to these.

============================================================
TASK 2 — Replace the scoped style block
============================================================

REPLACE the entire `<style scoped>` block in ProblemSubmissionsPanel.vue
with:

```css
<style scoped>
.problem-submissions-panel {
  display: grid;
  gap: 12px;
}

.problem-submissions-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.problem-submissions-panel__header .problem-section__title {
  margin-bottom: 0;
}

.problem-submission-list {
  display: grid;
  gap: 4px;
}

.problem-submission-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-soft);
}

.problem-submission-row:last-child {
  border-bottom: 0;
}

.problem-submission-row__language {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.problem-submission-row__metric,
.problem-submission-row__when {
  color: var(--color-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.problem-submission-row__view {
  margin-left: auto;
}

/* Narrow card: row may wrap onto multiple lines, but flex auto-margin
   keeps the button right-aligned on whichever line it lands. */
@media (max-width: 420px) {
  .problem-submission-row {
    row-gap: 4px;
  }
}
</style>
```

The old `.problem-submission-row__main` and `.problem-submission-row__meta`
classes are deleted along with the grid layout. The new layout works as:
- Normal/wide left column: everything fits on one flex line, button
  pinned to the right edge via `margin-left: auto`.
- Narrow left column: items wrap; `margin-left: auto` still right-aligns
  the button on whichever wrapped line it ends up on (Flexbox auto-margin
  resolves per line).

============================================================
TASK 3 — typecheck
============================================================

Run `npm run typecheck -w @aioj/web-user`. Must exit 0. Capture last
10 lines for the report.

============================================================
VERIFICATION
============================================================

1. `git status --short` — EXPECTED EXACTLY:
     M  apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
     M  docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md
     ?? docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md
   If anything else appears, REVERT it before reporting.

2. Focused diff for ProblemSubmissionsPanel.vue (full, ~40 lines
   context is OK since the file is small).

3. Last 10 lines of typecheck output.

4. Manual verification checklist (raw, for the human reviewer to run
   later in a browser):
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

5. 3-sentence Chinese summary of what changed, the chosen button
   `type` (outline vs secondary, and why), and the wrap behavior
   guarantee.

OUTPUT
Write all sections into:
  `docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md`
Then update inbox top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`
- `docs/codex-exchange/inbox/2026-05-25-2100-submissions-row-layout.md` (Status only)
- `docs/codex-exchange/outbox/2026-05-25-2100-submissions-row-layout.md` (new)

**Read for reference (do not modify)**:
- `apps/web-user/src/components/common/StatusChip.vue` — confirm chip
  accepts an external `class` prop (it should, via $attrs fall-through)
- `apps/web-user/src/styles.css` — color tokens
  (`--color-border-soft`, `--color-text-secondary`, `--color-text-muted`)
  used in the new style block already exist; no styles.css change needed
- Arco Design Vue button docs (Modal/Button @ 2.58) — verify `outline`
  type renders a rectangular bordered button. Fall back to `secondary`
  only if `outline` is not exposed in this version

**Hard 禁区**:
- backend/**
- packages/api-client/**
- packages/i18n/**
- apps/web-user/src/components/submission/**
- apps/web-user/src/views/**
- apps/web-user/src/styles.css
- any V*.sql under backend/api-contract/src/main/resources/db/migration/

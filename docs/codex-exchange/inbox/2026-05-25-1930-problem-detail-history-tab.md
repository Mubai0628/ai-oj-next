# Task: 做题页左侧新增「历史提交」tab + 抽取共享 SubmissionDetailModal
## Status: done
## Created by: Claude @ 2026-05-25T19:30+08:00
## Linked: outbox/2026-05-25-1930-problem-detail-history-tab.md

---

## Prompt

ROLE
You are Codex executing a task designed by Claude. Read this entire
file before starting. When done, write the full report to
`docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md`
using the section structure defined in `docs/codex-exchange/README.md`
(## Status / ## Created by / ## Linked / ## Report / ## Next-action hint).
Then update THIS file's `## Status:` line at the top from `new` to
`done` (or `blocked` with reason).

GOAL
On the student problem-detail page (apps/web-user), add a "My
submissions" area inside the LEFT card. It shows the current user's
submission history FOR THIS PROBLEM ONLY. Each row has a "view"
action that opens a centered modal with the submission's source code
and judging info. Reuse the modal that already works in
`apps/web-user/src/views/SubmissionsView.vue` by extracting it into a
shared component so both pages share one implementation.

CURRENT STATE (verified by Claude before issuing this task)
- `apps/web-user/src/views/ProblemDetailView.vue` uses `SplitPane`
  with `<ProblemPane>` on the left and `<EditorPane>` on the right
- `apps/web-user/src/components/problem/ProblemPane.vue` renders
  problem intro + `<ProblemTabs>` + per-tab `<section>` panels;
  current tabs are `statement` and `related`
- `apps/web-user/src/components/problem/ProblemTabs.vue` builds the
  tabs array from i18n keys `problems.descriptionTab` /
  `problems.relatedTab`. `ProblemTabKey` is defined in
  `apps/web-user/src/types/problem-workspace.ts`
- `apps/web-user/src/views/SubmissionsView.vue` ALREADY has a working
  centered `<a-modal>` with class `submission-detail-modal` (fixed by
  registering Arco `Modal` globally + `:render-to-body` +
  `:popup-container="'body'"`). The styles live in
  `apps/web-user/src/styles.css`
- `packages/api-client` exports `api.mySubmissions(params)` which hits
  `/api/v1/submissions?mine=true&...`. It accepts `problemId` in
  `SubmissionListParams`. It also exports `api.submission(id)` for the
  detail (which returns `code` in the response). `EntityId` is `string`
- Submission list response items DO NOT carry `code` (server strips it
  in list mode — this is correct). Only `api.submission(id)` returns it
- `StatusChip`, `EmptyState`, `PageHeader`, `BaseCard` already exist in
  `apps/web-user/src/components/common/`

CONSTRAINTS (hard)
- Frontend only. NO backend changes. NO `packages/api-client` changes.
  NO `V*.sql` migrations. NO new HTTP endpoints.
- The modal MUST be byte-identical in behavior on `/submissions` after
  refactor. Do NOT change list pagination, filter logic, stats logic,
  or any URL on that page.
- Reuse `api.mySubmissions` and `api.submission`. The list call in the
  new panel MUST include `problemId` so the server returns only this
  problem's records for the current user.
- All visible strings MUST go through i18n. Add new keys in zh-CN AND
  en-US synchronously inside `packages/i18n/src/messages.ts`. Do not
  rename existing keys.
- Keep `EntityId` as string everywhere (16+ digit safety). Do not
  coerce to Number.
- Do NOT stage or commit. Working tree changes only.

============================================================
TASK 1 — Extract shared SubmissionDetailModal component
============================================================

Create: `apps/web-user/src/components/submission/SubmissionDetailModal.vue`

Props:
  - `visible: boolean` (with `v-model:visible` support → declare emit
    `'update:visible': [value: boolean]`)
  - `submissionId: EntityId | null`

Internal state:
  - `loading: ref(false)`
  - `error: ref('')`
  - `detail: ref<SubmissionResponse | null>(null)`

Behavior:
  - `watch([() => props.visible, () => props.submissionId], ...)`:
    when visible turns true AND submissionId is non-null AND
    (detail is null OR detail.id !== submissionId), fetch
    `api.submission(submissionId)`. On error, set `error` from
    `ApiError.userMessage` (fallback to `Error.message` then to
    `t('submissions.viewLoadFailed')`).
  - When visible turns false: reset `detail`, `error`, `loading` so the
    next open does not flash stale content. Use `unmount-on-close` on
    the modal as a belt-and-suspenders measure.

Template:
  - One `<a-modal>` with the SAME props the current SubmissionsView
    modal uses:
      `v-model:visible` (computed-style, emit update:visible)
      `:title="t('submissions.viewCodeTitle')"`
      `:footer="false"`
      `:width="780"`
      `:mask-closable="true"`
      `:esc-to-close="true"`
      `:render-to-body="true"`
      `:popup-container="'body'"`
      `unmount-on-close`
      `modal-class="submission-detail-modal"`
  - Body is BYTE-IDENTICAL to the existing SubmissionsView modal body:
    `<a-spin>` wrap, error `<a-alert>`, meta grid div, judge-message
    `<a-alert>`, code head bar with copy button, code body wrapper
    with `<MdPreview>` / `<a-empty>` fallback
  - Copy-code button calls `navigator.clipboard.writeText(detail.code)`
    and shows `Message.success(t('submissions.viewCodeCopied'))`

i18n: use EXISTING `submissions.*` keys. Do not introduce new keys in
this task.

============================================================
TASK 2 — Refactor SubmissionsView.vue to use the shared modal
============================================================

In `apps/web-user/src/views/SubmissionsView.vue`:
  - DELETE the inline `<a-modal>...</a-modal>` block (currently ~lines
    82-128)
  - DELETE refs/computed/functions that move into the shared component:
      `detailLoading`, `detailError`, `detail`, `detailCodeMarkdown`,
      `copyDetailCode`, `modalContainer`
  - KEEP `detailVisible` ref. ADD `detailSubmissionId = ref<EntityId
    | null>(null)`
  - REPLACE `openDetail(item)` body with:
        detailSubmissionId.value = item.id;
        detailVisible.value = true;
    (no API call here anymore — modal self-fetches)
  - Add to template (anywhere appropriate, e.g. just before `</section>`):
        <SubmissionDetailModal
          v-model:visible="detailVisible"
          :submission-id="detailSubmissionId"
        />
  - Add import. Remove now-unused imports (`MdPreview`, `Message` if not
    used elsewhere in this file, the preview.css import if only modal
    used it — check usages first; keep imports that are still used).

After this task, behavior on `/submissions` MUST be identical to before.

============================================================
TASK 3 — Extend ProblemTabs with a "submissions" tab
============================================================

In `apps/web-user/src/types/problem-workspace.ts`:
  - Locate `ProblemTabKey` type alias / union. Add `'submissions'` to it.
    Final union order is exactly: `'statement' | 'submissions' | 'related'`

In `apps/web-user/src/components/problem/ProblemTabs.vue`:
  - Insert a new tab BETWEEN statement and related (final order:
    statement → submissions → related):
        { key: 'submissions', label: t('problems.submissionsTab') }
  - Do not change the existing two tabs or their order relative to each
    other

============================================================
TASK 4 — Create ProblemSubmissionsPanel
============================================================

Create: `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`

Props:
  - `problemId: EntityId`

Emits:
  - `'view': [submissionId: EntityId]`

Behavior:
  - `loading: ref(false)`, `error: ref('')`, `records: ref<SubmissionResponse[]>([])`
  - `loadList()` calls `api.mySubmissions({ problemId: props.problemId,
    page: 1, pageSize: 50 })`. Set `records.value = page.records`. On
    error: set `error` via ApiError.userMessage. Always reset
    `loading` in finally.
  - `watch(() => props.problemId, loadList, { immediate: true })`.
    This ensures the panel reloads when the user navigates between
    different problems on the same page (router param change).
  - Provide a manual `<a-button>` Refresh in the panel header that
    re-triggers `loadList`. Disable while loading.

Template:
  - Top row: small header `{t('problems.submissionsTab')}` on the
    left, refresh button on the right (use `t('problems.submissionsRefresh')`)
  - Loading: `<a-spin>` covering the list area
  - Error: `<a-alert type="error" closable @close="error = ''">{{ error }}</a-alert>`
  - Empty: `<EmptyState :title="t('problems.submissionsEmptyTitle')"
    :description="t('problems.submissionsEmptyHint')" />`
  - Non-empty: a compact `<ul>` or `<div class="list">` (NOT another
    `<table>` — the left card is already narrow). Each row:
      `<StatusChip>` + `<span>language</span>` +
      `<span>{timeMillis ?? '-'} ms</span>` +
      `<span>{memoryKb ? `${Math.round(memoryKb/1024)} MB` : '-'}</span>` +
      `<time>{formatRelative(createdAt)}</time>` +
      `<a-button size="mini" @click="$emit('view', item.id)">
        {t('problems.submissionsView')}</a-button>`
  - Add scoped styles so rows fit the narrow left column without
    horizontal scroll. Wrap status+language on small widths.

Helper `formatRelative(iso)`:
  - Pure-JS, no library. e.g. "<1 分钟前" / "3 分钟前" / "2 小时前" /
    "昨天 14:30" / fallback to `new Date(iso).toLocaleString()` if >7
    days
  - Use i18n keys `problems.relativeJustNow` /
    `problems.relativeMinutesAgo` / `problems.relativeHoursAgo` /
    `problems.relativeYesterday` for the labels

============================================================
TASK 5 — Wire panel + modal into ProblemPane / ProblemDetailView
============================================================

`apps/web-user/src/components/problem/ProblemPane.vue`:
  - Import `ProblemSubmissionsPanel`
  - Add a third branch BETWEEN the existing statement section and the
    related section:
        <section v-if="activeTab === 'submissions'" class="problem-tab-panel">
          <ProblemSubmissionsPanel
            :problem-id="problem.id"
            @view="$emit('view-submission', $event)"
          />
        </section>
  - Add `'view-submission': [submissionId: EntityId]` to `defineEmits`

`apps/web-user/src/views/ProblemDetailView.vue`:
  - Import `SubmissionDetailModal`
  - Add refs:
        const submissionModalVisible = ref(false);
        const viewingSubmissionId = ref<EntityId | null>(null);
  - Add handler:
        function onViewSubmission(id: EntityId) {
          viewingSubmissionId.value = id;
          submissionModalVisible.value = true;
        }
  - On `<ProblemPane>`: bind `@view-submission="onViewSubmission"`
  - Add to template, AFTER `<AiAssistantWorkspaceDrawer>` block:
        <SubmissionDetailModal
          v-model:visible="submissionModalVisible"
          :submission-id="viewingSubmissionId"
        />
  - Import `EntityId` from `@aioj/api-client` if not already imported

============================================================
TASK 6 — i18n
============================================================

In `packages/i18n/src/messages.ts`, under BOTH `zh-CN.problems` and
`en-US.problems` (synchronously, preserving all existing keys):

  submissionsTab           : '我的提交'                / 'My submissions'
  submissionsEmptyTitle    : '本题还没有提交记录'        / 'No submissions for this problem yet'
  submissionsEmptyHint     : '在右侧编辑器写代码并提交，记录会出现在这里。' / 'Write code in the editor on the right and submit — records appear here.'
  submissionsView          : '查看'                  / 'View'
  submissionsRefresh       : '刷新'                  / 'Refresh'
  submissionsLoadFailed    : '加载提交记录失败'         / 'Failed to load submissions'
  relativeJustNow          : '刚刚'                  / 'just now'
  relativeMinutesAgo       : '{n} 分钟前'             / '{n} min ago'
  relativeHoursAgo         : '{n} 小时前'             / '{n} h ago'
  relativeYesterday        : '昨天 {time}'            / 'Yesterday {time}'

============================================================
TASK 7 — typecheck
============================================================

Run `npm run typecheck -w @aioj/web-user`. MUST exit 0. If it fails,
fix and re-run. Show last 10 lines of output.

============================================================
VERIFICATION (run in order, write each into the outbox report)
============================================================

1. `git status --short` — expected entries (any order):
     ?? apps/web-user/src/components/submission/
     ?? apps/web-user/src/components/submission/SubmissionDetailModal.vue
     ?? apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue
     M  apps/web-user/src/components/problem/ProblemPane.vue
     M  apps/web-user/src/components/problem/ProblemTabs.vue
     M  apps/web-user/src/views/ProblemDetailView.vue
     M  apps/web-user/src/views/SubmissionsView.vue
     M  apps/web-user/src/types/problem-workspace.ts
     M  packages/i18n/src/messages.ts
     M  docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md   (Status update)
     ?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md   (new report)
   Any other file MUST be reverted.

2. Focused diffs (~15 lines context) for each modified file.

3. Full content of the two NEW Vue components.

4. Last 10 lines of typecheck output.

5. Manual verification checklist (raw, for the human reviewer):
   [ ] Open `/problems/{some id}` → left card shows 3 tabs in order:
       描述 / 我的提交 / 相关
   [ ] Click "我的提交" → loading spin → list of THIS user's submissions
       FOR THIS PROBLEM ONLY (or empty state)
   [ ] Click "查看" on a row → modal opens centered with backdrop;
       shows meta, judge message, code body with syntax highlight
   [ ] Click backdrop / press ESC → modal closes
   [ ] Open `/submissions` → page works exactly as before; the same
       modal opens via the row "查看" button
   [ ] Switch the language picker zh ↔ en → all new labels render
   [ ] Submit a new solution from the right editor → switch back to
       "我的提交" tab → click refresh → new entry appears at the top
   [ ] Navigate to a different problem → the "我的提交" tab list
       reloads showing that problem's records (watch immediate test)

6. 5-7 sentence Chinese summary covering:
   - which component was extracted and why
   - which files changed and the single-direction data flow
     (panel emits view → DetailView holds modal state)
   - i18n keys added
   - any deviation from this prompt (and why)
   - behavioral guarantee on /submissions (regression-free)

OUTPUT
Write all six sections into:
  `docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md`
Use the file template documented in `docs/codex-exchange/README.md`.
Then update THIS file's top `## Status: new` → `## Status: done`.

DO NOT commit, stage, push.

## Constraints / Files in scope

**Touch (write)**:
- apps/web-user/src/components/submission/SubmissionDetailModal.vue (new)
- apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue (new)
- apps/web-user/src/components/problem/ProblemPane.vue
- apps/web-user/src/components/problem/ProblemTabs.vue
- apps/web-user/src/views/ProblemDetailView.vue
- apps/web-user/src/views/SubmissionsView.vue
- apps/web-user/src/types/problem-workspace.ts
- packages/i18n/src/messages.ts
- docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md (Status only)
- docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md (new)

**Read for reference (do not modify)**:
- apps/web-user/src/styles.css (`.submission-detail-modal` rules already
  there from previous task — verify they cover the shared component)
- apps/web-user/src/main.ts (confirms Arco `Modal` is registered;
  if missing, that is a regression — report it but do not auto-fix)
- packages/api-client/src/index.ts (read `api.mySubmissions`,
  `api.submission`, `SubmissionResponse`, `SubmissionListParams`)
- apps/web-user/src/components/common/{StatusChip,EmptyState,BaseCard,PageHeader}.vue

**Hard禁区**:
- backend/**
- packages/api-client/**
- any V*.sql under backend/api-contract/src/main/resources/db/migration/
- any other docs/ file not listed above

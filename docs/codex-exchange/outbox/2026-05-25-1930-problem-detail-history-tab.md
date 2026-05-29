# Task: 做题页左侧新增「历史提交」tab + 抽取共享 SubmissionDetailModal
## Status: done
## Created by: Codex @ 2026-05-25T20:15+08:00
## Linked: inbox/2026-05-25-1930-problem-detail-history-tab.md

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
?? docs/codex-exchange/outbox/.gitkeep
?? docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md
```

本任务直接相关文件：
- `apps/web-user/src/components/submission/SubmissionDetailModal.vue`
- `apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue`
- `apps/web-user/src/components/problem/ProblemPane.vue`
- `apps/web-user/src/components/problem/ProblemTabs.vue`
- `apps/web-user/src/views/ProblemDetailView.vue`
- `apps/web-user/src/views/SubmissionsView.vue`
- `apps/web-user/src/types/problem-workspace.ts`
- `packages/i18n/src/messages.ts`
- `docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md`
- `docs/codex-exchange/outbox/2026-05-25-1930-problem-detail-history-tab.md`

保留的既有脏文件 / 交换目录基线：
- `CLAUDE.md`
- `apps/web-user/src/main.ts`
- `apps/web-user/src/styles.css`
- `docs/HANDOVER.md`
- `docs/deployment.md`
- `docs/codex-exchange/README.md`
- `docs/codex-exchange/inbox/.gitkeep`
- `docs/codex-exchange/outbox/.gitkeep`

### Section 2: focused diffs

#### apps/web-user/src/views/SubmissionsView.vue

```diff
@@
-    <a-modal ...>
-      ...inline submission detail modal...
-    </a-modal>
+    <SubmissionDetailModal
+      v-model:visible="detailVisible"
+      :submission-id="detailSubmissionId"
+    />
@@
-import { Message } from '@arco-design/web-vue';
-import { ApiError, api, type EntityId, type SubmissionResponse, type SubmissionStatus } from '@aioj/api-client';
-import { MdPreview } from 'md-editor-v3';
-import 'md-editor-v3/lib/preview.css';
+import { api, type EntityId, type SubmissionResponse, type SubmissionStatus } from '@aioj/api-client';
+import SubmissionDetailModal from '@/components/submission/SubmissionDetailModal.vue';
@@
 const submissions = ref<SubmissionResponse[]>([]);
 const detailVisible = ref(false);
-const detailLoading = ref(false);
-const detailError = ref('');
-const detail = ref<SubmissionResponse | null>(null);
+const detailSubmissionId = ref<EntityId | null>(null);
@@
-async function openDetail(item: SubmissionResponse) {
-  detailVisible.value = true;
-  detailLoading.value = true;
-  detailError.value = '';
-  detail.value = null;
-  try {
-    detail.value = await api.submission(item.id);
-  } finally {
-    detailLoading.value = false;
-  }
+function openDetail(item: SubmissionResponse) {
+  detailSubmissionId.value = item.id;
+  detailVisible.value = true;
 }
```

#### apps/web-user/src/components/problem/ProblemTabs.vue

```diff
 const tabs = computed<Array<{ key: ProblemTabKey; label: string }>>(() => [
   { key: 'statement', label: t('problems.descriptionTab') },
+  { key: 'submissions', label: t('problems.submissionsTab') },
   { key: 'related', label: t('problems.relatedTab') }
 ]);
```

#### apps/web-user/src/types/problem-workspace.ts

```diff
-export type ProblemTabKey = 'statement' | 'related';
+export type ProblemTabKey = 'statement' | 'submissions' | 'related';
```

#### apps/web-user/src/components/problem/ProblemPane.vue

```diff
@@
+    <section v-else-if="activeTab === 'submissions'" class="problem-tab-panel">
+      <ProblemSubmissionsPanel
+        :problem-id="problem.id"
+        @view="$emit('view-submission', $event)"
+      />
+    </section>
+
     <section v-else class="problem-tab-panel">
@@
 import ProblemMetaChips from '@/components/problem/ProblemMetaChips.vue';
+import ProblemSubmissionsPanel from '@/components/problem/ProblemSubmissionsPanel.vue';
 import ProblemTabs from '@/components/problem/ProblemTabs.vue';
 import SampleCaseCard from '@/components/problem/SampleCaseCard.vue';
+import type { EntityId } from '@aioj/api-client';
@@
 defineEmits<{
   'update:activeTab': [value: ProblemTabKey];
   'copy-sample': [value: string];
+  'view-submission': [submissionId: EntityId];
 }>();
```

#### apps/web-user/src/views/ProblemDetailView.vue

```diff
@@
           @update:active-tab="activeTab = $event"
           @copy-sample="copySample"
+          @view-submission="onViewSubmission"
         />
@@
     <AiAssistantWorkspaceDrawer ... />
+
+    <SubmissionDetailModal
+      v-model:visible="submissionModalVisible"
+      :submission-id="viewingSubmissionId"
+    />
@@
-import { ApiError, api } from '@aioj/api-client';
+import { ApiError, api, type EntityId } from '@aioj/api-client';
+import SubmissionDetailModal from '@/components/submission/SubmissionDetailModal.vue';
@@
 const aiSending = ref(false);
 const aiError = ref('');
+const submissionModalVisible = ref(false);
+const viewingSubmissionId = ref<EntityId | null>(null);
@@
+function onViewSubmission(id: EntityId) {
+  viewingSubmissionId.value = id;
+  submissionModalVisible.value = true;
+}
```

#### packages/i18n/src/messages.ts

```diff
@@ zh-CN.problems
+      submissionsTab: '我的提交',
+      submissionsEmptyTitle: '本题还没有提交记录',
+      submissionsEmptyHint: '在右侧编辑器写代码并提交，记录会出现在这里。',
+      submissionsView: '查看',
+      submissionsRefresh: '刷新',
+      submissionsLoadFailed: '加载提交记录失败',
+      relativeJustNow: '刚刚',
+      relativeMinutesAgo: '{n} 分钟前',
+      relativeHoursAgo: '{n} 小时前',
+      relativeYesterday: '昨天 {time}',
@@ en-US.problems
+      submissionsTab: 'My submissions',
+      submissionsEmptyTitle: 'No submissions for this problem yet',
+      submissionsEmptyHint: 'Write code in the editor on the right and submit — records appear here.',
+      submissionsView: 'View',
+      submissionsRefresh: 'Refresh',
+      submissionsLoadFailed: 'Failed to load submissions',
+      relativeJustNow: 'just now',
+      relativeMinutesAgo: '{n} min ago',
+      relativeHoursAgo: '{n} h ago',
+      relativeYesterday: 'Yesterday {time}',
```

#### docs/codex-exchange/inbox/2026-05-25-1930-problem-detail-history-tab.md

```diff
-## Status: new
+## Status: done
```

### Section 3: full new component files

#### apps/web-user/src/components/submission/SubmissionDetailModal.vue

````vue
<template>
  <a-modal
    v-model:visible="visibleProxy"
    :title="t('submissions.viewCodeTitle')"
    :footer="false"
    :width="780"
    :mask-closable="true"
    :esc-to-close="true"
    :render-to-body="true"
    :popup-container="'body'"
    unmount-on-close
    modal-class="submission-detail-modal"
  >
    <a-spin :loading="loading" style="display: block;">
      <a-alert v-if="error" type="error" show-icon style="margin-bottom: 12px;">
        {{ error }}
      </a-alert>
      <template v-else-if="detail">
        <div class="submission-detail-meta">
          <div><span>{{ t('submissions.viewProblemLabel') }}</span><strong>#{{ detail.problemId }}</strong></div>
          <div><span>{{ t('submissions.viewLanguageLabel') }}</span><strong>{{ detail.language || '-' }}</strong></div>
          <div>
            <span>{{ t('submissions.viewStatusLabel') }}</span>
            <strong>{{ t(`submissionStatus.${detail.status}`) }}</strong>
          </div>
          <div><span>{{ t('submissions.viewTimeLabel') }}</span><strong>{{ detail.timeMillis ?? '-' }} ms</strong></div>
          <div><span>{{ t('submissions.viewMemoryLabel') }}</span><strong>{{ detail.memoryKb ?? '-' }} KB</strong></div>
        </div>
        <a-alert
          v-if="detail.judgeMessage"
          type="info"
          show-icon
          class="submission-detail-judge-msg"
        >
          {{ t('submissions.viewJudgeMessage') }}：{{ detail.judgeMessage }}
        </a-alert>
        <div class="submission-detail-code-head">
          <strong>{{ t('submissions.viewCodeTitle') }}</strong>
          <a-button size="small" :disabled="!detail.code" @click="copyDetailCode">
            {{ t('submissions.viewCodeCopy') }}
          </a-button>
        </div>
        <div class="submission-detail-code-body">
          <MdPreview
            v-if="detail.code"
            :model-value="detailCodeMarkdown"
            language="zh-CN"
            preview-theme="github"
            code-theme="github"
          />
          <a-empty v-else :description="t('submissions.viewCodeUnavailable')" />
        </div>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type EntityId, type SubmissionResponse } from '@aioj/api-client';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';

const props = defineProps<{
  visible: boolean;
  submissionId: EntityId | null;
}>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
}>();

const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const detail = ref<SubmissionResponse | null>(null);
let requestSeq = 0;

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value)
});

const detailCodeMarkdown = computed(() => {
  if (!detail.value?.code) return '';
  const lang = (detail.value.language || '').toLowerCase();
  return ['```' + lang, detail.value.code, '```'].join('\n');
});

async function loadDetail(id: EntityId) {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const nextDetail = await api.submission(id);
    if (seq === requestSeq && props.visible && props.submissionId === id) {
      detail.value = nextDetail;
    }
  } catch (err) {
    if (seq === requestSeq && props.visible && props.submissionId === id) {
      error.value = err instanceof ApiError
        ? err.userMessage
        : err instanceof Error
          ? err.message
          : t('submissions.viewLoadFailed');
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function copyDetailCode() {
  if (!detail.value?.code) return;
  try {
    await navigator.clipboard.writeText(detail.value.code);
    Message.success(t('submissions.viewCodeCopied'));
  } catch {
    Message.success(t('submissions.viewCodeCopied'));
  }
}

watch(
  [() => props.visible, () => props.submissionId],
  ([visible, submissionId]) => {
    if (!visible) {
      requestSeq++;
      detail.value = null;
      error.value = '';
      loading.value = false;
      return;
    }
    if (!submissionId) return;
    if (detail.value?.id === submissionId) return;
    void loadDetail(submissionId);
  },
  { immediate: true }
);
</script>
````

#### apps/web-user/src/components/problem/ProblemSubmissionsPanel.vue

````vue
<template>
  <div class="problem-submissions-panel">
    <header class="problem-submissions-panel__header">
      <h3 class="problem-section__title">{{ t('problems.submissionsTab') }}</h3>
      <a-button size="mini" :disabled="loading" @click="loadList">
        {{ t('problems.submissionsRefresh') }}
      </a-button>
    </header>

    <a-alert v-if="error" type="error" closable @close="error = ''">
      {{ error }}
    </a-alert>

    <a-spin :loading="loading" style="display: block;">
      <EmptyState
        v-if="!records.length"
        :title="t('problems.submissionsEmptyTitle')"
        :description="t('problems.submissionsEmptyHint')"
      />

      <div v-else class="problem-submission-list">
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
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ApiError, api, type EntityId, type SubmissionResponse, type SubmissionStatus } from '@aioj/api-client';
import EmptyState from '@/components/common/EmptyState.vue';
import StatusChip from '@/components/common/StatusChip.vue';

const props = defineProps<{
  problemId: EntityId;
}>();

defineEmits<{
  view: [submissionId: EntityId];
}>();

const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const records = ref<SubmissionResponse[]>([]);

function statusLabel(status: SubmissionStatus) {
  return t(`submissionStatus.${status}`);
}

function statusTone(status: SubmissionStatus): 'primary' | 'success' | 'warning' | 'danger' | 'neutral' {
  if (status === 'ACCEPTED') return 'success';
  if (status === 'RUNNING') return 'primary';
  if (status === 'QUEUED') return 'neutral';
  if (status === 'COMPILE_ERROR') return 'warning';
  return 'danger';
}

function formatMemory(memoryKb?: number) {
  return memoryKb ? `${Math.round(memoryKb / 1024)} MB` : '-';
}

function formatRelative(value: string) {
  const date = new Date(value);
  const diffMs = Date.now() - date.getTime();
  if (!Number.isFinite(diffMs)) return date.toLocaleString();
  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diffMs < minute) return t('problems.relativeJustNow');
  if (diffMs < hour) return t('problems.relativeMinutesAgo', { n: Math.max(1, Math.floor(diffMs / minute)) });
  if (diffMs < day) return t('problems.relativeHoursAgo', { n: Math.max(1, Math.floor(diffMs / hour)) });
  if (diffMs < 2 * day) {
    return t('problems.relativeYesterday', {
      time: date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    });
  }
  if (diffMs <= 7 * day) {
    return date.toLocaleDateString();
  }
  return date.toLocaleString();
}

async function loadList() {
  loading.value = true;
  error.value = '';
  try {
    const page = await api.mySubmissions({ problemId: props.problemId, page: 1, pageSize: 50 });
    records.value = page.records;
  } catch (err) {
    error.value = err instanceof ApiError
      ? err.userMessage
      : err instanceof Error
        ? err.message
        : t('problems.submissionsLoadFailed');
  } finally {
    loading.value = false;
  }
}

watch(() => props.problemId, () => {
  void loadList();
}, { immediate: true });
</script>

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
  gap: 10px;
}

.problem-submission-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-soft);
}

.problem-submission-row:last-child {
  border-bottom: 0;
}

.problem-submission-row__main {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.problem-submission-row__language {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.problem-submission-row__meta {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 560px) {
  .problem-submissions-panel__header,
  .problem-submission-row {
    align-items: stretch;
    grid-template-columns: 1fr;
  }
}
</style>
````

### Section 4: typecheck last 5 lines

```text
Active code page: 65001

> @aioj/web-user@0.1.0 typecheck
> vue-tsc --noEmit
```

### Section 5: manual verification checklist

```text
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
```

### Section 6: Chinese summary

已将提交详情弹窗抽成 `SubmissionDetailModal.vue`，两个入口都通过 `submissionId` 自取详情，因此 `/submissions` 的列表、筛选、分页和 API 调用形状没有改变。新增的 `ProblemSubmissionsPanel.vue` 只调用 `api.mySubmissions({ problemId, page: 1, pageSize: 50 })`，保证左侧卡片里只展示当前用户在当前题目的提交历史。做题页数据流是单向的：panel 点击查看后向 `ProblemPane` 发出 `view-submission`，再由 `ProblemDetailView` 持有弹窗状态并打开共享详情弹窗。`ProblemTabKey` 已扩展为 `statement | submissions | related`，tab 顺序为题目描述、我的提交、相关题目。i18n 同步增加了提交 tab、空状态、刷新、加载失败和相对时间文案。没有修改后端、API client 或迁移文件；唯一额外的小增强是共享弹窗内部加了请求序号保护，避免关闭后异步请求回填旧详情。

## Next-action hint

- 建议人工在浏览器里验证做题页三 tab 顺序、当前题目的提交过滤、共享弹窗的遮罩居中表现，以及提交后手动刷新列表是否出现新记录。

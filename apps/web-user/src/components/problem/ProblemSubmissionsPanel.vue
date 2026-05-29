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
  if (status === 'MEMORY_LIMIT_EXCEEDED') return 'warning';
  if (status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
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

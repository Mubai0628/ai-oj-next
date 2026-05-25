<template>
  <section class="page-stack submissions-page">
    <PageHeader :eyebrow="t('submissions.eyebrow')" :title="t('submissions.title')" :description="t('submissions.description')">
      <template #actions>
        <a-button type="primary" :loading="loading" @click="loadSubmissions">{{ t('common.refresh') }}</a-button>
      </template>
    </PageHeader>

    <section class="submission-stats-grid">
      <article v-for="stat in stats" :key="stat.label" class="submission-stat-card base-card">
        <span>{{ stat.label }}</span>
        <strong>{{ stat.value }}</strong>
        <small :class="`tone-${stat.tone}`">{{ stat.meta }}</small>
      </article>
    </section>

    <BaseCard class="submission-filter-card">
      <a-input v-model="filters.keyword" :placeholder="t('submissions.keywordPlaceholder')" allow-clear />
      <a-select v-model="filters.status" :placeholder="t('common.status')" allow-clear>
        <a-option value="">{{ t('submissions.allStatuses') }}</a-option>
        <a-option v-for="status in statuses" :key="status" :value="status">{{ statusLabel(status) }}</a-option>
      </a-select>
      <a-select v-model="filters.language" :placeholder="t('common.language')" allow-clear>
        <a-option value="">{{ t('submissions.allLanguages') }}</a-option>
        <a-option v-for="language in languageOptions" :key="language" :value="language">{{ language }}</a-option>
      </a-select>
      <input v-model="filters.startDate" class="native-date-input" type="date" :aria-label="t('submissions.startDate')" />
      <input v-model="filters.endDate" class="native-date-input" type="date" :aria-label="t('submissions.endDate')" />
      <div class="filter-actions">
        <a-button @click="resetFilters">{{ t('submissions.resetFilters') }}</a-button>
        <a-button type="primary">{{ t('common.apply') }}</a-button>
      </div>
    </BaseCard>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>
    <a-spin :loading="loading" :tip="t('submissions.loading')">
      <EmptyState
        v-if="!filteredSubmissions.length"
        :title="submissions.length ? t('submissions.noFilteredTitle') : t('submissions.emptyTitle')"
        :description="submissions.length ? t('submissions.noFilteredDescription') : t('submissions.emptyDescription')"
      >
        <a-button v-if="submissions.length" @click="resetFilters">{{ t('submissions.resetFilters') }}</a-button>
        <router-link v-else class="dashboard-action dashboard-action--primary" to="/problems">{{ t('submissions.goPractice') }}</router-link>
      </EmptyState>

      <div v-else class="table-shell submissions-table-shell">
        <table>
          <thead>
            <tr>
              <th>{{ t('common.id') }}</th>
              <th>{{ t('submissions.problem') }}</th>
              <th>{{ t('common.status') }}</th>
              <th>{{ t('common.language') }}</th>
              <th>{{ t('common.time') }}</th>
              <th>{{ t('common.memory') }}</th>
              <th>{{ t('common.created') }}</th>
              <th>{{ t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredSubmissions" :key="item.id">
              <td class="mono-id">#{{ shortId(item.id) }}</td>
              <td>
                <router-link :to="`/problems/${item.problemId}`">{{ t('dashboard.problemLabel', { id: shortId(item.problemId) }) }}</router-link>
              </td>
              <td><StatusChip :label="statusLabel(item.status)" :tone="statusTone(item.status)" /></td>
              <td>{{ item.language }}</td>
              <td>{{ item.timeMillis ?? '-' }} ms</td>
              <td>{{ item.memoryKb ? `${Math.round(item.memoryKb / 1024)} MB` : '-' }}</td>
              <td>{{ formatDate(item.createdAt) }}</td>
              <td>
                <button class="table-action-link" type="button" @click="openDetail(item)">
                  {{ t('submissions.view') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </a-spin>

    <a-modal
      v-model:visible="detailVisible"
      :title="t('submissions.viewCodeTitle')"
      :footer="false"
      :width="780"
      unmount-on-close
    >
      <a-spin :loading="detailLoading" style="display: block;">
        <a-alert v-if="detailError" type="error" show-icon style="margin-bottom: 12px;">
          {{ detailError }}
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
          <MdPreview
            v-if="detail.code"
            :model-value="detailCodeMarkdown"
            language="zh-CN"
            preview-theme="github"
            code-theme="github"
          />
          <a-empty v-else :description="t('submissions.viewCodeUnavailable')" />
        </template>
      </a-spin>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type EntityId, type SubmissionResponse, type SubmissionStatus } from '@aioj/api-client';
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';
import BaseCard from '@/components/common/BaseCard.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import StatusChip from '@/components/common/StatusChip.vue';

const { t } = useI18n();
const loading = ref(false);
const error = ref('');
const submissions = ref<SubmissionResponse[]>([]);
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detail = ref<SubmissionResponse | null>(null);
const statuses: SubmissionStatus[] = [
  'QUEUED',
  'RUNNING',
  'ACCEPTED',
  'WRONG_ANSWER',
  'COMPILE_ERROR',
  'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'SYSTEM_ERROR'
];
const filters = reactive<{ keyword: string; status: SubmissionStatus | ''; language: string; startDate: string; endDate: string }>({
  keyword: '',
  status: '',
  language: '',
  startDate: '',
  endDate: ''
});

const languageOptions = computed(() => Array.from(new Set(submissions.value.map((item) => item.language).filter(Boolean))).sort());

const detailCodeMarkdown = computed(() => {
  if (!detail.value?.code) return '';
  const lang = (detail.value.language || '').toLowerCase();
  return ['```' + lang, detail.value.code, '```'].join('\n');
});

const filteredSubmissions = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  const start = filters.startDate ? new Date(`${filters.startDate}T00:00:00`).getTime() : Number.NEGATIVE_INFINITY;
  const end = filters.endDate ? new Date(`${filters.endDate}T23:59:59`).getTime() : Number.POSITIVE_INFINITY;
  return submissions.value.filter((item) => {
    const created = new Date(item.createdAt).getTime();
    const matchesKeyword = !keyword || String(item.id).toLowerCase().includes(keyword) || String(item.problemId).toLowerCase().includes(keyword);
    const matchesStatus = !filters.status || item.status === filters.status;
    const matchesLanguage = !filters.language || item.language === filters.language;
    return matchesKeyword && matchesStatus && matchesLanguage && created >= start && created <= end;
  });
});

const acceptedCount = computed(() => submissions.value.filter((item) => item.status === 'ACCEPTED').length);
const failedCount = computed(() => submissions.value.filter((item) => item.status !== 'ACCEPTED' && item.status !== 'QUEUED' && item.status !== 'RUNNING').length);
const latestStatus = computed(() => submissions.value[0]?.status);

const stats = computed(() => [
  { label: t('submissions.total'), value: submissions.value.length, meta: t('submissions.totalMeta'), tone: 'primary' },
  { label: t('submissions.accepted'), value: acceptedCount.value, meta: t('submissions.acceptedMeta'), tone: 'success' },
  { label: t('submissions.failed'), value: failedCount.value, meta: t('submissions.failedMeta'), tone: 'danger' },
  {
    label: t('submissions.latestStatus'),
    value: latestStatus.value ? statusLabel(latestStatus.value) : t('dashboard.noSubmissions'),
    meta: latestStatus.value ? t('submissions.latestMeta') : t('submissions.noLatestMeta'),
    tone: latestStatus.value ? statusTone(latestStatus.value) : 'neutral'
  }
]);

function shortId(id: EntityId) {
  const text = String(id);
  return text.length > 8 ? text.slice(-8) : text;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

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

function resetFilters() {
  filters.keyword = '';
  filters.status = '';
  filters.language = '';
  filters.startDate = '';
  filters.endDate = '';
}

async function openDetail(item: SubmissionResponse) {
  detailVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detail.value = null;
  try {
    detail.value = await api.submission(item.id);
  } catch (err) {
    detailError.value = err instanceof ApiError
      ? err.userMessage
      : err instanceof Error
        ? err.message
        : t('submissions.viewLoadFailed');
  } finally {
    detailLoading.value = false;
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

async function loadSubmissions() {
  loading.value = true;
  error.value = '';
  try {
    const page = await api.mySubmissions({ page: 1, pageSize: 50 });
    submissions.value = page.records;
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('submissions.loadFailed');
  } finally {
    loading.value = false;
  }
}

onMounted(loadSubmissions);
</script>

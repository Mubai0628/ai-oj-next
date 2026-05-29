<template>
  <section class="ai-drafts-layout">
    <a-card :bordered="false" class="draft-generate-card">
      <div class="draft-card-head">
        <div class="draft-title-row">
          <span class="draft-icon">✦</span>
          <div>
            <h2>{{ t('drafts.generate') }}</h2>
            <p>{{ t('drafts.generateCopy') }}</p>
          </div>
        </div>
        <div v-if="quota" class="draft-quota-chip">
          <span>{{ t('drafts.quotaToday', { used: quota.usedToday, total: quota.dailyLimit }) }}</span>
          <span>·</span>
          <span>{{ t('drafts.quotaMonth', { used: quota.usedThisMonth, total: quota.monthlyLimit }) }}</span>
        </div>
        <div v-else-if="quotaError" class="draft-quota-chip draft-quota-chip--muted">
          {{ t('drafts.quotaUnavailable') }}
        </div>
      </div>

      <a-alert v-if="generateError" type="error" show-icon class="form-alert">
        {{ generateError }}
      </a-alert>
      <a-form :model="form" layout="vertical" class="draft-generate-form">
        <a-form-item
          :label="t('drafts.topic')"
          :validate-status="fieldError('topic') ? 'error' : undefined"
          :help="fieldError('topic') || undefined"
        >
          <a-input v-model="form.topic" :max-length="100" :placeholder="t('drafts.topicPlaceholder')" />
          <template #extra>{{ t('problems.charCount', { count: form.topic.length, max: 100 }) }}</template>
        </a-form-item>

        <a-form-item
          :label="t('common.difficulty')"
          :validate-status="fieldError('difficulty') ? 'error' : undefined"
          :help="fieldError('difficulty') || undefined"
        >
          <a-select v-model="form.difficulty">
            <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">
              {{ difficultyIcon(difficulty) }} {{ t(`difficulty.${difficulty}`) }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item
          :label="t('drafts.teachingGoal')"
          :validate-status="fieldError('teachingGoal') ? 'error' : undefined"
          :help="fieldError('teachingGoal') || undefined"
        >
          <a-textarea v-model="form.teachingGoal" :max-length="200" :placeholder="t('drafts.teachingGoalPlaceholder')" :auto-size="{ minRows: 5, maxRows: 8 }" />
          <template #extra>{{ t('problems.charCount', { count: form.teachingGoal.length, max: 200 }) }}</template>
        </a-form-item>

        <a-button v-if="!generating" type="primary" long class="draft-generate-button" @click="generateDraft">
          {{ t('drafts.generate') }}
        </a-button>
        <div v-else class="draft-generate-progress" role="status" aria-live="polite">
          <a-spin />
          <div class="draft-generate-progress-text">
            <strong>{{ generateStage }}</strong>
            <small>{{ t('drafts.elapsedSeconds', { seconds: Math.ceil(generateElapsedMs / 1000) }) }}</small>
          </div>
          <a-button size="small" status="danger" @click="cancelGenerate">{{ t('common.cancel') }}</a-button>
        </div>
      </a-form>

      <div class="draft-tip">
        <span>i</span>
        <p>{{ t('drafts.generateTip') }}</p>
      </div>
    </a-card>

    <a-card :bordered="false" class="draft-review-card">
      <div class="draft-review-head">
        <a-tabs v-model:active-key="activeStatus" type="rounded" class="draft-tabs" @change="setStatus">
          <a-tab-pane key="PENDING_REVIEW" :title="t('drafts.pendingWithCount', { count: pendingCount })" />
          <a-tab-pane key="APPROVED" :title="t('drafts.approvedWithCount', { count: approvedCount })" />
        </a-tabs>
        <a-button :loading="loading" @click="loadDrafts">{{ t('common.refresh') }}</a-button>
      </div>

      <div class="draft-filter-row">
        <a-space wrap>
          <span class="draft-filter-label">{{ t('drafts.filterValidation') }}</span>
          <a-radio-group v-model="validationFilter" type="button" @change="loadDrafts">
            <a-radio value="">{{ t('drafts.filterAll') }}</a-radio>
            <a-radio value="VALID">{{ t('drafts.filterValid') }}</a-radio>
            <a-radio value="INVALID">{{ t('drafts.filterInvalid') }}</a-radio>
          </a-radio-group>
          <a-checkbox v-model="mineOnly" @change="loadDrafts">{{ t('drafts.filterMine') }}</a-checkbox>
          <a-select v-model="sortOrder" class="draft-sort-select" @change="loadDrafts">
            <a-option value="newest">{{ t('drafts.sortNewest') }}</a-option>
            <a-option value="oldest">{{ t('drafts.sortOldest') }}</a-option>
          </a-select>
        </a-space>
      </div>

      <a-alert v-if="listError" type="error" show-icon>
        {{ listError }}
      </a-alert>
      <a-table
        v-if="drafts.length || loading"
        :data="drafts"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 940 }"
        @row-click="openDetail"
      >
        <template #columns>
          <a-table-column :title="t('common.title')" data-index="title" :width="220" />
          <a-table-column :title="t('common.difficulty')" :width="120">
            <template #cell="{ record }">
              <a-tag :color="difficultyColor(record.difficulty)">{{ t(`difficulty.${record.difficulty}`) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.tags')" :width="220">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag v-for="tag in record.tags" :key="tag" class="soft-tag">{{ tag }}</a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column :title="t('drafts.validation')" :width="140">
            <template #cell="{ record }">
              <a-tooltip v-if="record.validationErrors?.length" :content="record.validationErrors.join('; ')">
                <a-tag color="orange">{{ validationStatusLabel(record.validationStatus) }}</a-tag>
              </a-tooltip>
              <a-tag v-else color="green">{{ validationStatusLabel(record.validationStatus) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('drafts.import')" :width="150">
            <template #cell="{ record }">
              <a-tag v-if="record.importedProblemId" color="green">#{{ record.importedProblemId }}</a-tag>
              <a-tag v-else>{{ t('drafts.notImported') }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.actions')" :width="120" fixed="right">
            <template #cell="{ record }">
              <a-button size="small" type="outline" @click.stop="openDetail(record)">
                {{ t('drafts.detailTabsEdit') }}
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>

      <div v-if="!loading && drafts.length === 0" class="draft-empty-state">
        <div class="empty-illustration">AI</div>
        <h3>{{ t('drafts.emptyTitle') }}</h3>
        <p>{{ t('drafts.emptyDescription') }}</p>
        <a-button type="primary" @click="focusGenerate">{{ t('drafts.generateNow') }}</a-button>
      </div>

      <div class="draft-flow">
        <article v-for="(step, index) in flowSteps" :key="step">
          <span>{{ index + 1 }}</span>
          <strong>{{ step }}</strong>
        </article>
      </div>
    </a-card>

    <a-modal v-model:visible="rejectModalVisible" :title="t('drafts.reject')" :ok-loading="rejecting" @ok="confirmReject">
      <a-form :model="rejectForm" layout="vertical">
        <a-form-item :label="t('drafts.rejectReasonLabel')">
          <a-textarea
            v-model="rejectForm.reasonNote"
            :max-length="500"
            :placeholder="t('drafts.rejectReasonPlaceholder')"
            :auto-size="{ minRows: 4, maxRows: 7 }"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <AiDraftDetailDrawer
      v-model:visible="detailVisible"
      :draft="selectedDraft"
      @refined="handleDraftUpdated"
      @regenerated="handleDraftUpdated"
      @approve="(draft) => approveDraft(draft.id)"
      @import-draft="importDraft"
      @reject="openRejectModal"
      @delete="(draft) => deleteDraft(draft.id)"
      @open-draft="openDraftById"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type AiUsageResponse, type Difficulty, type EntityId, type ProblemDraftResponse } from '@aioj/api-client';
import AiDraftDetailDrawer from '@/components/AiDraftDetailDrawer.vue';
import { useAuthStore } from '@/stores/auth';

type DraftStatus = 'PENDING_REVIEW' | 'APPROVED';
type ValidationFilter = '' | 'VALID' | 'INVALID';
type SortOrder = 'newest' | 'oldest';

const { t } = useI18n();
const auth = useAuthStore();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const form = reactive({
  topic: '',
  difficulty: 'EASY' as Difficulty,
  teachingGoal: ''
});
const activeStatus = ref<DraftStatus>('PENDING_REVIEW');
const validationFilter = ref<ValidationFilter>('');
const mineOnly = ref(false);
const sortOrder = ref<SortOrder>('newest');
const drafts = ref<ProblemDraftResponse[]>([]);
const pendingCount = ref(0);
const approvedCount = ref(0);
const loading = ref(false);
const generating = ref(false);
const rejecting = ref(false);
const quota = ref<AiUsageResponse | null>(null);
const quotaError = ref(false);
const generateController = ref<AbortController | null>(null);
const generateStage = ref('');
const generateElapsedMs = ref(0);
const rejectModalVisible = ref(false);
const rejectTarget = ref<ProblemDraftResponse | null>(null);
const detailVisible = ref(false);
const selectedDraft = ref<ProblemDraftResponse | null>(null);
const rejectForm = reactive({
  reasonNote: ''
});
const listError = ref('');
const generateError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const flowSteps = computed(() => [t('drafts.flowGenerate'), t('drafts.flowReview'), t('drafts.flowApprove')]);
const generateStageList = computed(() => [
  t('drafts.stageThinking'),
  t('drafts.stageDrafting'),
  t('drafts.stageTestcases'),
  t('drafts.stagePersisting')
]);

function difficultyIcon(difficulty: Difficulty) {
  return ({ EASY: '●', MEDIUM: '◆', HARD: '▲', CHALLENGE: '✦' } as Record<Difficulty, string>)[difficulty];
}

function difficultyColor(difficulty: string) {
  return ({ EASY: 'green', MEDIUM: 'orange', HARD: 'red', CHALLENGE: 'purple' } as Record<string, string>)[difficulty] || 'arcoblue';
}

function validationStatusLabel(status: string) {
  const key = `validationStatus.${status}`;
  const translated = t(key);
  return translated === key ? status : translated;
}

function fieldError(path: string) {
  return fieldErrors.value[path];
}

function draftListParams(status: DraftStatus, pageSize: number) {
  return {
    page: 1,
    pageSize,
    status,
    validationStatus: validationFilter.value || undefined,
    creatorUserId: mineOnly.value ? auth.profile?.userId : undefined,
    sort: sortOrder.value
  };
}

async function loadDrafts() {
  loading.value = true;
  listError.value = '';
  try {
    const oppositeStatus = activeStatus.value === 'PENDING_REVIEW' ? 'APPROVED' : 'PENDING_REVIEW';
    const [page, otherCount] = await Promise.all([
      api.problemDrafts(draftListParams(activeStatus.value, 50)),
      api.problemDrafts(draftListParams(oppositeStatus, 1))
    ]);
    drafts.value = page.records;
    if (activeStatus.value === 'PENDING_REVIEW') {
      pendingCount.value = page.total;
      approvedCount.value = otherCount.total;
    } else {
      approvedCount.value = page.total;
      pendingCount.value = otherCount.total;
    }
  } catch (caught) {
    listError.value = caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function loadQuota() {
  quotaError.value = false;
  try {
    quota.value = await api.usage();
  } catch {
    quota.value = null;
    quotaError.value = true;
  }
}

async function setStatus(value: string | number) {
  activeStatus.value = value as DraftStatus;
  await loadDrafts();
}

function focusGenerate() {
  const input = document.querySelector<HTMLInputElement>('.draft-generate-card input');
  input?.focus();
}

function openDetail(draft: ProblemDraftResponse | Record<string, unknown>) {
  selectedDraft.value = draft as ProblemDraftResponse;
  detailVisible.value = true;
}

async function openDraftById(id: EntityId) {
  try {
    selectedDraft.value = await api.problemDraft(id);
    detailVisible.value = true;
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.loadFailed')));
  }
}

async function handleDraftUpdated(draft: ProblemDraftResponse) {
  selectedDraft.value = draft;
  await loadDrafts();
  await loadQuota();
}

async function generateDraft() {
  if (generating.value) return;
  generating.value = true;
  generateError.value = '';
  fieldErrors.value = {};
  const controller = new AbortController();
  generateController.value = controller;
  generateStage.value = generateStageList.value[0];
  generateElapsedMs.value = 0;
  const start = Date.now();
  const tickHandle = window.setInterval(() => {
    generateElapsedMs.value = Date.now() - start;
    const index = Math.min(Math.floor(generateElapsedMs.value / 4000), generateStageList.value.length - 1);
    generateStage.value = generateStageList.value[index];
  }, 200);
  try {
    const draft = await api.generateDraft({
      topic: form.topic.trim(),
      difficulty: form.difficulty,
      teachingGoal: form.teachingGoal.trim() || undefined
    }, {
      signal: controller.signal
    });
    Message.success(t('drafts.generated'));
    if (activeStatus.value === 'PENDING_REVIEW') {
      drafts.value.unshift(draft);
      pendingCount.value += 1;
    } else {
      activeStatus.value = 'PENDING_REVIEW';
      await loadDrafts();
    }
    await loadQuota();
    fieldErrors.value = {};
  } catch (caught) {
    if ((caught as DOMException)?.name === 'AbortError') {
      generateError.value = t('drafts.generateCanceled');
    } else if (caught instanceof ApiError) {
      fieldErrors.value = caught.details ?? {};
      generateError.value = caught.userMessage;
    } else {
      generateError.value = caught instanceof Error ? caught.message : t('drafts.generateFailed');
    }
  } finally {
    window.clearInterval(tickHandle);
    generating.value = false;
    generateController.value = null;
    generateStage.value = '';
  }
}

function cancelGenerate() {
  generateController.value?.abort();
}

async function approveDraft(id: EntityId) {
  try {
    const approved = await api.approveDraft(id, false);
    if (selectedDraft.value?.id === id) selectedDraft.value = approved;
    Message.success(t('drafts.approvedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.approveFailed')));
  }
}

function openRejectModal(draft: ProblemDraftResponse) {
  rejectTarget.value = draft;
  rejectForm.reasonNote = '';
  rejectModalVisible.value = true;
}

async function confirmReject() {
  if (!rejectTarget.value) return;
  rejecting.value = true;
  try {
    await api.rejectDraft(rejectTarget.value.id, rejectForm.reasonNote.trim() || undefined);
    Message.success(t('drafts.rejectedMessage'));
    rejectModalVisible.value = false;
    rejectTarget.value = null;
    rejectForm.reasonNote = '';
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.rejectFailed')));
  } finally {
    rejecting.value = false;
  }
}

async function importDraft(draft: ProblemDraftResponse) {
  try {
    const imported = await api.approveDraft(draft.id, true);
    if (selectedDraft.value?.id === draft.id) selectedDraft.value = imported;
    Message.success(imported.importedProblemId ? t('drafts.importedAs', { id: imported.importedProblemId }) : t('drafts.approvedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.importFailed')));
  }
}

async function deleteDraft(id: EntityId) {
  try {
    await api.deleteDraft(id);
    if (selectedDraft.value?.id === id) {
      selectedDraft.value = null;
      detailVisible.value = false;
    }
    Message.success(t('drafts.deletedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.deleteFailed')));
  }
}

onMounted(() => {
  void loadDrafts();
  void loadQuota();
});
</script>

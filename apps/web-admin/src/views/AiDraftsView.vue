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
      </div>

      <a-alert v-if="generateError" type="error" show-icon class="form-alert" :content="generateError" />
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

        <a-button type="primary" long class="draft-generate-button" :loading="generating" @click="generateDraft">
          {{ generating ? t('drafts.generating') : t('drafts.generate') }}
        </a-button>
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

      <a-alert v-if="listError" type="error" show-icon :content="listError" />
      <a-table v-if="drafts.length || loading" :data="drafts" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1040 }">
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
          <a-table-column :title="t('common.actions')" :width="330" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button size="small" :disabled="activeStatus === 'APPROVED'" @click="approveDraft(record.id)">
                  {{ t('drafts.approve') }}
                </a-button>
                <a-popconfirm :content="t('drafts.rejectConfirm')" @ok="openRejectModal(record)">
                  <a-button size="small" status="warning" :disabled="!!record.importedProblemId">{{ t('drafts.reject') }}</a-button>
                </a-popconfirm>
                <a-popconfirm :content="t('drafts.importConfirm')" @ok="importDraft(record)">
                  <a-button size="small" type="primary" :disabled="!!record.importedProblemId">{{ t('drafts.import') }}</a-button>
                </a-popconfirm>
                <a-popconfirm :content="t('drafts.deleteConfirm')" @ok="deleteDraft(record.id)">
                  <a-button size="small" type="outline" status="danger" :disabled="!!record.importedProblemId">{{ t('common.delete') }}</a-button>
                </a-popconfirm>
              </a-space>
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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type Difficulty, type EntityId, type ProblemDraftResponse } from '@aioj/api-client';

type DraftStatus = 'PENDING_REVIEW' | 'APPROVED';

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const form = reactive({
  topic: '',
  difficulty: 'EASY' as Difficulty,
  teachingGoal: ''
});
const activeStatus = ref<DraftStatus>('PENDING_REVIEW');
const drafts = ref<ProblemDraftResponse[]>([]);
const pendingCount = ref(0);
const approvedCount = ref(0);
const loading = ref(false);
const generating = ref(false);
const rejecting = ref(false);
const rejectModalVisible = ref(false);
const rejectTarget = ref<ProblemDraftResponse | null>(null);
const rejectForm = reactive({
  reasonNote: ''
});
const listError = ref('');
const generateError = ref('');
const fieldErrors = ref<Record<string, string>>({});

const flowSteps = computed(() => [t('drafts.flowGenerate'), t('drafts.flowReview'), t('drafts.flowApprove')]);

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

async function loadDrafts() {
  loading.value = true;
  listError.value = '';
  try {
    const oppositeStatus = activeStatus.value === 'PENDING_REVIEW' ? 'APPROVED' : 'PENDING_REVIEW';
    const [page, otherCount] = await Promise.all([
      api.problemDrafts({ page: 1, pageSize: 50, status: activeStatus.value }),
      api.problemDrafts({ page: 1, pageSize: 1, status: oppositeStatus })
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

async function setStatus(value: string | number) {
  activeStatus.value = value as DraftStatus;
  await loadDrafts();
}

function focusGenerate() {
  const input = document.querySelector<HTMLInputElement>('.draft-generate-card input');
  input?.focus();
}

async function generateDraft() {
  generating.value = true;
  generateError.value = '';
  fieldErrors.value = {};
  try {
    const draft = await api.generateDraft({
      topic: form.topic.trim(),
      difficulty: form.difficulty,
      teachingGoal: form.teachingGoal.trim() || undefined
    });
    Message.success(t('drafts.generated'));
    if (activeStatus.value === 'PENDING_REVIEW') {
      drafts.value.unshift(draft);
      pendingCount.value += 1;
    } else {
      activeStatus.value = 'PENDING_REVIEW';
      await loadDrafts();
    }
    fieldErrors.value = {};
  } catch (caught) {
    if (caught instanceof ApiError) {
      fieldErrors.value = caught.details ?? {};
      generateError.value = caught.userMessage;
    } else {
      generateError.value = caught instanceof Error ? caught.message : t('drafts.generateFailed');
    }
  } finally {
    generating.value = false;
  }
}

async function approveDraft(id: EntityId) {
  try {
    await api.approveDraft(id, false);
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
    Message.success(imported.importedProblemId ? t('drafts.importedAs', { id: imported.importedProblemId }) : t('drafts.approvedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.importFailed')));
  }
}

async function deleteDraft(id: EntityId) {
  try {
    await api.deleteDraft(id);
    Message.success(t('drafts.deletedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof ApiError ? caught.userMessage : (caught instanceof Error ? caught.message : t('drafts.deleteFailed')));
  }
}

onMounted(loadDrafts);
</script>

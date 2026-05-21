<template>
  <section class="drafts-layout">
    <a-card :title="t('drafts.generate')" :bordered="false">
      <a-alert v-if="generateError" type="error" show-icon class="form-alert" :content="generateError" />
      <a-form :model="form" layout="vertical">
        <a-form-item :label="t('drafts.topic')">
          <a-input v-model="form.topic" />
        </a-form-item>
        <div class="form-grid two">
          <a-form-item :label="t('common.difficulty')">
            <a-select v-model="form.difficulty">
              <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">{{ t(`difficulty.${difficulty}`) }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('drafts.count')">
            <a-input-number v-model="form.count" :min="1" :max="5" />
          </a-form-item>
        </div>
        <a-form-item :label="t('drafts.teachingGoal')">
          <a-textarea v-model="form.teachingGoal" :auto-size="{ minRows: 4, maxRows: 7 }" />
        </a-form-item>
        <a-button type="primary" long :loading="generating" @click="generateDraft">{{ t('drafts.generate') }}</a-button>
      </a-form>
    </a-card>

    <section class="view-stack">
      <a-card :bordered="false">
        <div class="toolbar-row">
          <a-space wrap>
            <a-button :type="activeStatus === 'PENDING_REVIEW' ? 'primary' : 'secondary'" @click="setStatus('PENDING_REVIEW')">
              {{ t('drafts.pending') }}
            </a-button>
            <a-button :type="activeStatus === 'APPROVED' ? 'primary' : 'secondary'" @click="setStatus('APPROVED')">
              {{ t('drafts.approved') }}
            </a-button>
          </a-space>
          <a-button @click="loadDrafts">{{ t('common.refresh') }}</a-button>
        </div>
      </a-card>

      <a-alert v-if="listError" type="error" show-icon :content="listError" />
      <a-card :bordered="false">
        <a-table :data="drafts" :loading="loading" :pagination="false" row-key="id">
          <template #columns>
            <a-table-column :title="t('common.title')" data-index="title" />
            <a-table-column :title="t('common.difficulty')" :width="130">
              <template #cell="{ record }">
                <a-tag>{{ t(`difficulty.${record.difficulty}`) }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column :title="t('common.tags')" :width="240">
              <template #cell="{ record }">
                <a-space wrap>
                  <a-tag v-for="tag in record.tags" :key="tag">{{ tag }}</a-tag>
                </a-space>
              </template>
            </a-table-column>
            <a-table-column :title="t('drafts.validation')" :width="160">
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
            <a-table-column :title="t('common.actions')" :width="260">
              <template #cell="{ record }">
                <a-space>
                  <a-button size="small" :disabled="activeStatus === 'APPROVED'" @click="approveDraft(record.id)">
                    {{ t('drafts.approve') }}
                  </a-button>
                  <a-popconfirm :content="t('drafts.importConfirm')" @ok="importDraft(record)">
                    <a-button size="small" type="primary" :disabled="!!record.importedProblemId">{{ t('drafts.import') }}</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </a-table-column>
          </template>
        </a-table>
        <a-empty v-if="!loading && drafts.length === 0" :description="t('drafts.empty')" />
      </a-card>

      <a-card v-if="selectedPreview" :title="t('drafts.preview')" :bordered="false">
        <h3>{{ selectedPreview.title }}</h3>
        <p class="statement">{{ selectedPreview.statement }}</p>
      </a-card>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { api, type Difficulty, type EntityId, type ProblemDraftResponse } from '@aioj/api-client';

type DraftStatus = 'PENDING_REVIEW' | 'APPROVED';

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const form = reactive({
  topic: '',
  difficulty: 'EASY' as Difficulty,
  count: 1,
  teachingGoal: ''
});
const activeStatus = ref<DraftStatus>('PENDING_REVIEW');
const drafts = ref<ProblemDraftResponse[]>([]);
const loading = ref(false);
const generating = ref(false);
const listError = ref('');
const generateError = ref('');

const selectedPreview = computed(() => drafts.value[0] || null);

function validationStatusLabel(status: string) {
  const key = `validationStatus.${status}`;
  const translated = t(key);
  return translated === key ? status : translated;
}

async function loadDrafts() {
  loading.value = true;
  listError.value = '';
  try {
    drafts.value = (await api.problemDrafts({ page: 1, pageSize: 50, status: activeStatus.value })).records;
  } catch (caught) {
    listError.value = caught instanceof Error ? caught.message : t('drafts.loadFailed');
  } finally {
    loading.value = false;
  }
}

async function setStatus(status: DraftStatus) {
  activeStatus.value = status;
  await loadDrafts();
}

async function generateDraft() {
  generating.value = true;
  generateError.value = '';
  try {
    const draft = await api.generateDraft({
      topic: form.topic.trim(),
      difficulty: form.difficulty,
      count: Number(form.count || 1),
      teachingGoal: form.teachingGoal.trim() || undefined
    });
    Message.success(t('drafts.generated'));
    if (activeStatus.value === 'PENDING_REVIEW') {
      drafts.value.unshift(draft);
    } else {
      activeStatus.value = 'PENDING_REVIEW';
      await loadDrafts();
    }
  } catch (caught) {
    generateError.value = caught instanceof Error ? caught.message : t('drafts.generateFailed');
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
    Message.error(caught instanceof Error ? caught.message : t('drafts.approveFailed'));
  }
}

async function importDraft(draft: ProblemDraftResponse) {
  try {
    const imported = await api.approveDraft(draft.id, true);
    Message.success(imported.importedProblemId ? t('drafts.importedAs', { id: imported.importedProblemId }) : t('drafts.approvedMessage'));
    await loadDrafts();
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('drafts.importFailed'));
  }
}

onMounted(loadDrafts);
</script>

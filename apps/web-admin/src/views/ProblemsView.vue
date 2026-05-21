<template>
  <section class="view-stack">
    <a-card :bordered="false">
      <div class="toolbar-row">
        <a-space wrap>
          <a-input-search v-model="filters.keyword" :placeholder="t('problems.searchAdminPlaceholder')" allow-clear @search="loadProblems" />
          <a-select v-model="filters.difficulty" :placeholder="t('common.difficulty')" allow-clear class="filter-control" @change="loadProblems">
            <a-option value="">{{ t('problems.allDifficulties') }}</a-option>
            <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">{{ difficultyLabel(difficulty) }}</a-option>
          </a-select>
          <a-input v-model="filters.tag" :placeholder="t('problems.tagPlaceholder')" class="filter-control" @press-enter="loadProblems" />
        </a-space>
        <a-space wrap>
          <a-button @click="loadProblems">{{ t('common.refresh') }}</a-button>
          <a-button type="primary" @click="openCreate">{{ t('problems.create') }}</a-button>
        </a-space>
      </div>
    </a-card>

    <a-alert v-if="error" type="error" show-icon :content="error" />
    <a-card :bordered="false" class="table-card">
      <a-table :data="problems" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 900 }">
        <template #columns>
          <a-table-column :title="t('common.id')" :width="110">
            <template #cell="{ record }">
              <span class="id-chip" :title="String(record.id)">#{{ shortId(record.id) }}</span>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.title')" data-index="title" :width="240" />
          <a-table-column :title="t('common.difficulty')" :width="96">
            <template #cell="{ record }">
              <a-tag :color="difficultyColor(record.difficulty)">{{ difficultyLabel(record.difficulty) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="t('common.tags')" :width="170">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag v-for="tag in record.tags" :key="tag">{{ tag }}</a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column :title="t('problems.limits')" :width="140">
            <template #cell="{ record }">
              {{ record.timeLimitMillis }} ms / {{ Math.round(record.memoryLimitKb / 1024) }} MB
            </template>
          </a-table-column>
          <a-table-column :title="t('common.actions')" :width="144">
            <template #cell="{ record }">
              <a-space>
                <a-button size="small" @click="openEdit(record)">{{ t('common.edit') }}</a-button>
                <a-popconfirm :content="t('problems.deleteConfirm')" @ok="deleteProblem(record.id)">
                  <a-button size="small" status="danger">{{ t('common.delete') }}</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && problems.length === 0" :description="t('problems.adminEmpty')" />
    </a-card>

    <a-modal
      v-model:visible="modalVisible"
      :title="editingId ? t('problems.editModal') : t('problems.createModal')"
      :ok-loading="saving"
      width="900px"
      @ok="saveProblem"
    >
      <a-form :model="form" layout="vertical">
        <div class="form-grid two">
          <a-form-item :label="t('problems.titleLabel')">
            <a-input v-model="form.title" />
          </a-form-item>
          <a-form-item :label="t('common.difficulty')">
            <a-select v-model="form.difficulty">
              <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">{{ difficultyLabel(difficulty) }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="t('common.tags')">
            <a-input v-model="tagText" :placeholder="t('problems.tagsPlaceholder')" />
          </a-form-item>
          <div class="form-grid two compact">
            <a-form-item :label="t('problems.timeLimit')">
              <a-input-number v-model="form.timeLimitMillis" :min="100" :step="100" hide-button>
                <template #suffix>ms</template>
              </a-input-number>
            </a-form-item>
            <a-form-item :label="t('problems.memoryLimit')">
              <a-input-number v-model="memoryLimitMb" :min="16" :step="16" hide-button>
                <template #suffix>MB</template>
              </a-input-number>
            </a-form-item>
          </div>
        </div>
        <a-form-item :label="t('problems.statement')">
          <a-textarea v-model="form.statement" :auto-size="{ minRows: 8, maxRows: 14 }" />
        </a-form-item>
        <div class="section-title">
          <h2>{{ t('problems.testCases') }}</h2>
          <a-button size="small" @click="addCase">{{ t('problems.addCase') }}</a-button>
        </div>
        <div class="case-list">
          <section v-for="(testCase, index) in form.testCases" :key="index" class="case-card">
            <div class="case-head">
              <strong>{{ t('problems.caseTitle', { index: index + 1 }) }}</strong>
              <a-space>
                <a-checkbox v-model="testCase.sample">{{ t('problems.sample') }}</a-checkbox>
                <a-button size="mini" status="danger" :disabled="form.testCases.length === 1" @click="removeCase(index)">
                  {{ t('common.remove') }}
                </a-button>
              </a-space>
            </div>
            <div class="form-grid two">
              <a-form-item :label="t('problems.input')">
                <a-textarea v-model="testCase.input" :auto-size="{ minRows: 3, maxRows: 6 }" />
              </a-form-item>
              <a-form-item :label="t('problems.expectedOutput')">
                <a-textarea v-model="testCase.expectedOutput" :auto-size="{ minRows: 3, maxRows: 6 }" />
              </a-form-item>
            </div>
          </section>
        </div>
        <TestcasePackageUploader :problem-id="editingId" />
      </a-form>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { api, type Difficulty, type EntityId, type ProblemPayload, type ProblemResponse, type TestCaseDto } from '@aioj/api-client';
import TestcasePackageUploader from '@/components/TestcasePackageUploader.vue';

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const problems = ref<ProblemResponse[]>([]);
const modalVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const tagText = ref('');
const filters = reactive<{ keyword: string; difficulty: Difficulty | ''; tag: string }>({
  keyword: '',
  difficulty: '',
  tag: ''
});
const form = reactive<ProblemPayload>({
  title: '',
  difficulty: 'EASY',
  statement: '',
  tags: [],
  testCases: [emptyCase()],
  timeLimitMillis: 1000,
  memoryLimitKb: 262144
});

const memoryLimitMb = computed({
  get: () => Math.round(form.memoryLimitKb / 1024),
  set: (value: number) => {
    form.memoryLimitKb = Number(value || 0) * 1024;
  }
});

function emptyCase(): TestCaseDto {
  return { input: '', expectedOutput: '', sample: true };
}

function difficultyColor(difficulty: Difficulty) {
  return ({ EASY: 'green', MEDIUM: 'orange', HARD: 'red', CHALLENGE: 'purple' } as Record<Difficulty, string>)[difficulty];
}

function difficultyLabel(difficulty: Difficulty) {
  return t(`difficulty.${difficulty}`);
}

function shortId(id: EntityId) {
  const text = String(id);
  return text.length > 8 ? text.slice(-8) : text;
}

function tagsFromText() {
  return tagText.value.split(',').map((tag) => tag.trim()).filter(Boolean);
}

function resetForm() {
  editingId.value = null;
  form.title = '';
  form.difficulty = 'EASY';
  form.statement = '';
  form.tags = [];
  form.testCases = [emptyCase()];
  form.timeLimitMillis = 1000;
  form.memoryLimitKb = 262144;
  tagText.value = '';
}

function openCreate() {
  resetForm();
  modalVisible.value = true;
}

async function openEdit(problem: ProblemResponse) {
  saving.value = false;
  try {
    const detail = await api.problem(problem.id);
    editingId.value = detail.id;
    form.title = detail.title;
    form.difficulty = detail.difficulty;
    form.statement = detail.statement;
    form.tags = [...detail.tags];
    form.testCases = detail.samples.length ? detail.samples.map((item) => ({ ...item })) : [emptyCase()];
    form.timeLimitMillis = detail.timeLimitMillis;
    form.memoryLimitKb = detail.memoryLimitKb;
    tagText.value = detail.tags.join(', ');
    modalVisible.value = true;
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('problems.loadOneFailed'));
  }
}

function addCase() {
  form.testCases.push({ input: '', expectedOutput: '', sample: false });
}

function removeCase(index: number) {
  form.testCases.splice(index, 1);
}

function payload(): ProblemPayload {
  return {
    title: form.title.trim(),
    difficulty: form.difficulty,
    statement: form.statement.trim(),
    tags: tagsFromText(),
    testCases: form.testCases.map((testCase, index) => ({
      input: testCase.input,
      expectedOutput: testCase.expectedOutput,
      sample: index === 0 ? true : testCase.sample
    })),
    timeLimitMillis: Number(form.timeLimitMillis || 1000),
    memoryLimitKb: Number(form.memoryLimitKb || 262144)
  };
}

async function loadProblems() {
  loading.value = true;
  error.value = '';
  try {
    problems.value = (await api.problems({ ...filters, page: 1, pageSize: 50 })).records;
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : t('problems.loadFailed');
  } finally {
    loading.value = false;
  }
}

async function saveProblem() {
  saving.value = true;
  try {
    if (editingId.value) {
      await api.updateProblem(editingId.value, payload());
      Message.success(t('problems.updated'));
    } else {
      await api.createProblem(payload());
      Message.success(t('problems.created'));
    }
    modalVisible.value = false;
    await loadProblems();
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('problems.saveFailed'));
  } finally {
    saving.value = false;
  }
}

async function deleteProblem(id: EntityId) {
  try {
    await api.deleteProblem(id);
    Message.success(t('problems.deleted'));
    await loadProblems();
  } catch (caught) {
    Message.error(caught instanceof Error ? caught.message : t('problems.deleteFailed'));
  }
}

onMounted(loadProblems);
</script>

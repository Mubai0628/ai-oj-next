<template>
  <section class="view-stack">
    <a-card :bordered="false" class="filter-card">
      <div class="toolbar-row">
        <a-space wrap>
          <a-input-search v-model="filters.keyword" :placeholder="t('problems.searchAdminPlaceholder')" allow-clear @search="loadProblems" />
          <a-select v-model="filters.difficulty" :placeholder="t('common.difficulty')" allow-clear class="filter-control" @change="loadProblems">
            <a-option value="">{{ t('problems.allDifficulties') }}</a-option>
            <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">{{ difficultyLabel(difficulty) }}</a-option>
          </a-select>
          <a-input
            v-model="filters.tag"
            :placeholder="t('problems.tagPlaceholder')"
            class="filter-control"
            allow-clear
            @press-enter="loadProblems"
            @blur="loadProblems"
            @clear="loadProblems"
          />
        </a-space>
        <a-space wrap>
          <a-button @click="loadProblems">{{ t('common.refresh') }}</a-button>
          <a-button type="primary" @click="openCreate">{{ t('problems.create') }}</a-button>
        </a-space>
      </div>
    </a-card>

    <a-alert v-if="error" type="error" show-icon>
      {{ error }}
    </a-alert>
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

    <ProblemEditorDrawer v-model:visible="editorVisible" :problem="selectedProblem" @saved="loadProblems" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { api, type Difficulty, type EntityId, type ProblemResponse } from '@aioj/api-client';
import ProblemEditorDrawer from '@/components/ProblemEditorDrawer.vue';

const { t } = useI18n();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const loading = ref(false);
const error = ref('');
const problems = ref<ProblemResponse[]>([]);
const editorVisible = ref(false);
const selectedProblem = ref<ProblemResponse | null>(null);
const filters = reactive<{ keyword: string; difficulty: Difficulty | ''; tag: string }>({
  keyword: '',
  difficulty: '',
  tag: ''
});

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

function openCreate() {
  selectedProblem.value = null;
  editorVisible.value = true;
}

function openEdit(problem: ProblemResponse) {
  selectedProblem.value = problem;
  editorVisible.value = true;
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

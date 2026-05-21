<template>
  <section class="page-stack">
    <PageHeader :eyebrow="t('problems.eyebrow')" :title="t('problems.title')" :description="t('problems.subtitle', { count: problems.length })">
      <template #actions>
        <a-button type="primary" :disabled="!problems.length" @click="goRandomProblem">{{ t('problems.random') }}</a-button>
        <a-button :loading="loading" @click="loadProblems">{{ t('common.refresh') }}</a-button>
      </template>
    </PageHeader>

    <BaseCard class="problem-filter-card">
      <a-input-search v-model="filters.keyword" :placeholder="t('problems.searchPlaceholder')" allow-clear @search="loadProblems" />
      <a-select v-model="filters.difficulty" :placeholder="t('common.difficulty')" allow-clear>
        <a-option value="">{{ t('problems.allDifficulties') }}</a-option>
        <a-option v-for="difficulty in difficulties" :key="difficulty" :value="difficulty">{{ difficultyLabel(difficulty) }}</a-option>
      </a-select>
      <a-select v-model="filters.tag" :placeholder="t('problems.tagPlaceholder')" allow-clear>
        <a-option value="">{{ t('problems.allTags') }}</a-option>
        <a-option v-for="tag in tagOptions" :key="tag" :value="tag">{{ tag }}</a-option>
      </a-select>
      <div class="filter-actions">
        <a-button @click="resetFilters">{{ t('problems.resetFilters') }}</a-button>
        <a-button type="primary" @click="loadProblems">{{ t('common.apply') }}</a-button>
      </div>
    </BaseCard>

    <div class="problem-tabs" aria-label="problem quick filters">
      <button class="problem-tab" :class="{ active: filters.difficulty === '' }" type="button" @click="setDifficulty('')">
        <span class="problem-tab__dot all" />
        {{ t('problems.allDifficulties') }}
        <small>{{ problems.length }}</small>
      </button>
      <button
        v-for="difficulty in difficulties"
        :key="difficulty"
        class="problem-tab"
        :class="{ active: filters.difficulty === difficulty }"
        type="button"
        @click="setDifficulty(difficulty)"
      >
        <span class="problem-tab__dot" :class="difficulty.toLowerCase()" />
        {{ difficultyLabel(difficulty) }}
        <small>{{ difficultyCount(difficulty) }}</small>
      </button>
    </div>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>
    <a-spin :loading="loading" :tip="t('problems.loading')">
      <EmptyState v-if="!problems.length" :title="t('problems.noResultsTitle')" :description="t('problems.noResultsDescription')">
        <a-button type="primary" @click="resetFilters">{{ t('problems.resetFilters') }}</a-button>
      </EmptyState>
      <section v-else class="problem-list-grid">
        <router-link v-for="problem in problems" :key="problem.id" class="problem-card base-card interactive" :to="`/problems/${problem.id}`">
          <div class="problem-card-head">
            <h2>{{ problem.title }}</h2>
            <DifficultyChip :difficulty="problem.difficulty" />
          </div>
          <p>{{ preview(problem.statement) }}</p>
          <div class="tag-row">
            <span v-for="tag in problem.tags" :key="tag" class="soft-tag">{{ tag }}</span>
          </div>
          <div class="problem-card-footer">
            <span>{{ t('problems.timeLimit') }} {{ problem.timeLimitMillis }} ms</span>
            <span>{{ t('problems.memoryLimit') }} {{ Math.round(problem.memoryLimitKb / 1024) }} MB</span>
            <strong>{{ t('problems.startPractice') }} <i /></strong>
          </div>
        </router-link>
      </section>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { api, type Difficulty, type ProblemResponse } from '@aioj/api-client';
import BaseCard from '@/components/common/BaseCard.vue';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';

const { t } = useI18n();
const router = useRouter();
const difficulties: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'CHALLENGE'];
const loading = ref(false);
const error = ref('');
const problems = ref<ProblemResponse[]>([]);
const filters = reactive<{ keyword: string; difficulty: Difficulty | ''; tag: string }>({
  keyword: '',
  difficulty: '',
  tag: ''
});

const tagOptions = computed(() => Array.from(new Set(problems.value.flatMap((problem) => problem.tags))).sort());

function preview(statement: string) {
  return statement.replace(/[#*_`]/g, '').replace(/\s+/g, ' ').slice(0, 150) || t('problems.openDetailFallback');
}

function difficultyLabel(difficulty: Difficulty) {
  return t(`difficulty.${difficulty}`);
}

function difficultyCount(difficulty: Difficulty) {
  return problems.value.filter((problem) => problem.difficulty === difficulty).length;
}

function setDifficulty(difficulty: Difficulty | '') {
  filters.difficulty = difficulty;
  void loadProblems();
}

function resetFilters() {
  filters.keyword = '';
  filters.difficulty = '';
  filters.tag = '';
  void loadProblems();
}

async function goRandomProblem() {
  if (!problems.value.length) return;
  const index = Math.floor(Math.random() * problems.value.length);
  await router.push(`/problems/${problems.value[index].id}`);
}

async function loadProblems() {
  loading.value = true;
  error.value = '';
  try {
    const page = await api.problems({
      page: 1,
      pageSize: 50,
      keyword: filters.keyword.trim(),
      difficulty: filters.difficulty,
      tag: filters.tag
    });
    problems.value = page.records;
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('problems.loadFailed');
  } finally {
    loading.value = false;
  }
}

onMounted(loadProblems);
</script>

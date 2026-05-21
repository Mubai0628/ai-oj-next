<template>
  <header class="problem-page__header">
    <nav class="problem-breadcrumb" aria-label="Breadcrumb">
      <router-link to="/problems">{{ t('problems.eyebrow') }}</router-link>
      <span>/</span>
      <span>{{ t('problems.detailTitle') }}</span>
      <span>/</span>
      <span>#{{ problem?.id || problemId }}</span>
    </nav>

    <div class="problem-title-row">
      <div class="problem-title-row__main">
        <div class="problem-title-lineup">
          <h1 class="problem-title">{{ problem?.title || t('problems.detailTitle') }}</h1>
          <DifficultyChip v-if="problem" :difficulty="problem.difficulty" />
        </div>
        <ProblemMetaChips v-if="problem" :problem="problem" />
      </div>

      <div class="problem-actions">
        <router-link to="/problems" class="problem-action problem-action--ghost">{{ t('problems.backToList') }}</router-link>
        <button class="problem-action problem-action--ghost" type="button" :disabled="loading" @click="$emit('refresh')">
          {{ t('common.refresh') }}
        </button>
        <button class="problem-action problem-action--disabled" type="button" disabled>
          {{ t('problems.favoriteUnavailable') }}
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import ProblemMetaChips from '@/components/problem/ProblemMetaChips.vue';
import type { ProblemDetailModel } from '@/types/problem-workspace';

defineProps<{
  problem: ProblemDetailModel | null;
  loading: boolean;
  problemId: string;
}>();

defineEmits<{
  refresh: [];
}>();

const { t } = useI18n();
</script>

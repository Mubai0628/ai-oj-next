<template>
  <section class="ai-problem-context">
    <span>{{ t('aiAssistant.currentProblem') }}</span>
    <h3>{{ problemTitle || t('ai.noProblem') }}</h3>
    <p v-if="problemId">#{{ problemId }}</p>
    <div class="ai-problem-context__meta">
      <DifficultyChip v-if="difficulty" :difficulty="difficulty" />
      <span v-for="tag in tags" :key="tag">#{{ tag }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import DifficultyChip from '@/components/common/DifficultyChip.vue';

defineProps<{
  problemId?: string | number;
  problemTitle?: string;
  difficulty?: string;
  tags?: string[];
}>();

const { t } = useI18n();
</script>

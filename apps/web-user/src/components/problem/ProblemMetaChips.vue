<template>
  <div class="problem-meta-chips">
    <span class="meta-chip">{{ t('problems.timeLimit') }} {{ problem.timeLimitMillis }} ms</span>
    <span class="meta-chip">{{ t('problems.memoryLimit') }} {{ problem.memoryLimitMb }} MB</span>
    <span class="meta-chip">{{ t('common.id') }} #{{ problem.id }}</span>
    <span v-for="tag in problem.tags" :key="tag" class="meta-chip meta-chip--tag">{{ tag }}</span>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import type { ProblemDetailModel } from '@/types/problem-workspace';

defineProps<{
  problem: ProblemDetailModel;
}>();

const { t } = useI18n();
</script>

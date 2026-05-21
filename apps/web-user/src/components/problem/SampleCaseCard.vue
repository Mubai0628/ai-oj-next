<template>
  <article class="sample-card">
    <header class="sample-card__header">
      <strong>{{ t('problems.sampleTitle', { index }) }}</strong>
      <button class="sample-card__copy" type="button" @click="$emit('copy', sample.input)">
        {{ t('problems.copySample') }}
      </button>
    </header>
    <div class="sample-grid">
      <section class="sample-block">
        <span class="sample-label">{{ t('problems.input') }}</span>
        <pre class="sample-code">{{ sample.input || `(${t('problems.emptyValue')})` }}</pre>
      </section>
      <section class="sample-block">
        <span class="sample-label">{{ t('problems.output') }}</span>
        <pre class="sample-code">{{ sample.output || `(${t('problems.emptyValue')})` }}</pre>
      </section>
    </div>
    <p v-if="sample.explanation" class="sample-explanation">{{ sample.explanation }}</p>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import type { ProblemSampleModel } from '@/types/problem-workspace';

defineProps<{
  sample: ProblemSampleModel;
  index: number;
}>();

defineEmits<{
  copy: [value: string];
}>();

const { t } = useI18n();
</script>

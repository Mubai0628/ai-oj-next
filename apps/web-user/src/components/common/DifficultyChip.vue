<template>
  <span class="difficulty-chip" :class="`tone-${tone}`">
    <span class="difficulty-chip__dot" />
    {{ text }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { Difficulty } from '@aioj/api-client';

const props = defineProps<{
  difficulty?: Difficulty | string;
  label?: string;
}>();

const { t } = useI18n();

const text = computed(() => {
  if (props.label) return props.label;
  if (!props.difficulty) return t('common.difficulty');
  return t(`difficulty.${props.difficulty as Difficulty}`);
});

const tone = computed(() => {
  if (props.difficulty === 'EASY') return 'easy';
  if (props.difficulty === 'MEDIUM') return 'medium';
  if (props.difficulty === 'HARD' || props.difficulty === 'CHALLENGE') return 'hard';
  return 'neutral';
});
</script>

<style scoped>
.difficulty-chip {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.difficulty-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.tone-easy {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.tone-medium {
  background: var(--color-warning-soft);
  color: #b45309;
}

.tone-hard {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.tone-neutral {
  background: var(--color-surface-soft);
  color: var(--color-text-muted);
}
</style>

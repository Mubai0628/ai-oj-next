<template>
  <span class="status-chip" :class="`tone-${resolvedTone}`">
    <span class="dot" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  label: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  status?: string;
}>();

const resolvedTone = computed(() => {
  if (props.tone) return props.tone;
  if (props.status === 'ACCEPTED' || props.status === 'EASY') return 'success';
  if (props.status === 'QUEUED' || props.status === 'RUNNING' || props.status === 'MEDIUM') return 'primary';
  if (props.status === 'HARD' || props.status === 'CHALLENGE' || props.status === 'SYSTEM_ERROR') return 'danger';
  if (props.status === 'WRONG_ANSWER'
      || props.status === 'TIME_LIMIT_EXCEEDED'
      || props.status === 'MEMORY_LIMIT_EXCEEDED'
      || props.status === 'OUTPUT_LIMIT_EXCEEDED') return 'warning';
  return 'neutral';
});
</script>

<style scoped>
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.tone-primary {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.tone-success {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.tone-warning {
  background: var(--color-warning-soft);
  color: #b45309;
}

.tone-danger {
  background: var(--color-danger-soft);
  color: var(--color-danger);
}

.tone-neutral {
  background: var(--color-surface-soft);
  color: var(--color-text-muted);
}
</style>

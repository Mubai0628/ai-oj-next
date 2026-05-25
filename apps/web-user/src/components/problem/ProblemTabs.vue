<template>
  <div class="problem-workspace-tabs" role="tablist" :aria-label="t('problems.detailTitle')">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="problem-workspace-tab"
      :class="{ 'problem-workspace-tab--active': modelValue === tab.key }"
      type="button"
      role="tab"
      :aria-selected="modelValue === tab.key"
      @click="$emit('update:modelValue', tab.key)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { ProblemTabKey } from '@/types/problem-workspace';

defineProps<{
  modelValue: ProblemTabKey;
}>();

defineEmits<{
  'update:modelValue': [value: ProblemTabKey];
}>();

const { t } = useI18n();

const tabs = computed<Array<{ key: ProblemTabKey; label: string }>>(() => [
  { key: 'statement', label: t('problems.descriptionTab') },
  { key: 'related', label: t('problems.relatedTab') }
]);
</script>

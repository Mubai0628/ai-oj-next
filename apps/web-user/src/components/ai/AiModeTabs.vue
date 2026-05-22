<template>
  <div class="ai-mode-tabs" role="tablist" :aria-label="t('aiAssistant.modeLabel')">
    <button
      v-for="item in modes"
      :key="item.value"
      class="ai-mode-tab"
      :class="{ 'ai-mode-tab--active': item.value === modelValue }"
      type="button"
      role="tab"
      :aria-selected="item.value === modelValue"
      @click="$emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AiMode } from '@/types/ai-assistant';

defineProps<{
  modelValue: AiMode;
}>();

defineEmits<{
  'update:modelValue': [value: AiMode];
}>();

const { t } = useI18n();

const modes = computed<Array<{ value: AiMode; label: string }>>(() => [
  { value: 'hint', label: t('aiAssistant.modes.hint') },
  { value: 'debug', label: t('aiAssistant.modes.debug') },
  { value: 'edge', label: t('aiAssistant.modes.edge') },
  { value: 'optimize', label: t('aiAssistant.modes.optimize') }
]);
</script>

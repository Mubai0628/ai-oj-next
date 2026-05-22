<template>
  <div class="ai-quick-chips">
    <button v-for="prompt in prompts" :key="prompt" type="button" @click="$emit('pick', prompt)">
      {{ prompt }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AiMode } from '@/types/ai-assistant';

const props = defineProps<{
  mode: AiMode;
}>();

defineEmits<{
  pick: [value: string];
}>();

const { t } = useI18n();

const prompts = computed(() => {
  const baseKey = `aiAssistant.quick.${props.mode}`;
  return [0, 1, 2, 3].map((index) => t(`${baseKey}[${index}]`));
});
</script>

<template>
  <article class="ai-message-bubble" :class="`ai-message-bubble--${message.role}`">
    <div v-if="message.role !== 'user'" class="ai-message-bubble__avatar">AI</div>
    <div class="ai-message-bubble__content">
      <div class="ai-message-bubble__meta">
        <strong>{{ message.role === 'user' ? t('ai.you') : t('ai.tutor') }}</strong>
        <span>{{ timeText }}</span>
      </div>
      <p v-if="message.content">{{ message.content }}</p>
      <p v-else class="ai-message-bubble__placeholder">{{ t('ai.streaming') }}</p>
      <small v-if="message.status === 'error'">{{ t('aiAssistant.messageFailed') }}</small>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AiMessage } from '@/types/ai-assistant';

const props = defineProps<{
  message: AiMessage;
}>();

const { t } = useI18n();

const timeText = computed(() => {
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(props.message.createdAt));
});
</script>

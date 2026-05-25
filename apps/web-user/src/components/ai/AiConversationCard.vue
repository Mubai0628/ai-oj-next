<template>
  <button class="ai-conversation-card" :class="{ 'ai-conversation-card--active': active }" type="button">
    <div class="ai-conversation-card__summary">
      <span class="ai-conversation-card__mode">{{ modeLabel }}</span>
      <strong>{{ conversation.title }}</strong>
    </div>
    <p v-if="!compact">{{ lastMessage || t('aiAssistant.emptyConversation') }}</p>
    <footer>
      <span>{{ messageCount }}</span>
      <time>{{ timeText }}</time>
    </footer>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AiConversation } from '@/types/ai-assistant';

const props = defineProps<{
  conversation: AiConversation;
  active?: boolean;
  compact?: boolean;
}>();

const { t } = useI18n();

const modeLabel = computed(() => t(`aiAssistant.modes.${props.conversation.mode}`));
const messageCount = computed(() => t('aiAssistant.messageCount', { count: props.conversation.messages.length }));
const lastMessage = computed(() => {
  const latest = [...props.conversation.messages].reverse().find((message) => message.content.trim());
  return latest?.content.replace(/\s+/g, ' ').slice(0, 72);
});
const timeText = computed(() => {
  return new Intl.DateTimeFormat(undefined, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(props.conversation.updatedAt));
});
</script>

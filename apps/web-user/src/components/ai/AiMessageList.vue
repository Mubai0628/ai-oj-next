<template>
  <div class="ai-message-list">
    <EmptyState v-if="!messages.length" :title="emptyTitle" :description="emptyDescription">
      <slot name="empty" />
    </EmptyState>
    <AiMessageBubble v-for="message in messages" v-else :key="message.id" :message="message" />
  </div>
</template>

<script setup lang="ts">
import AiMessageBubble from '@/components/ai/AiMessageBubble.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import type { AiMessage } from '@/types/ai-assistant';

defineProps<{
  messages: AiMessage[];
  emptyTitle: string;
  emptyDescription: string;
}>();
</script>

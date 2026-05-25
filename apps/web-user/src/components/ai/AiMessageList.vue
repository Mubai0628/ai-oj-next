<template>
  <div ref="listRef" class="ai-message-list">
    <EmptyState v-if="!messages.length" :title="emptyTitle" :description="emptyDescription">
      <slot name="empty" />
    </EmptyState>
    <AiMessageBubble v-for="message in messages" v-else :key="message.id" :message="message" :rich-markdown="richMarkdown" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import AiMessageBubble from '@/components/ai/AiMessageBubble.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import type { AiMessage } from '@/types/ai-assistant';

const props = defineProps<{
  messages: AiMessage[];
  emptyTitle: string;
  emptyDescription: string;
  richMarkdown?: boolean;
}>();

const listRef = ref<HTMLDivElement | null>(null);

const latestMessageSignature = computed(() => {
  const latest = props.messages[props.messages.length - 1];
  return [
    props.messages.length,
    latest?.id || '',
    latest?.content || '',
    latest?.status || ''
  ].join(':');
});

watch(latestMessageSignature, async () => {
  await nextTick();
  const list = listRef.value;
  if (!list) return;
  list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
}, { flush: 'post' });
</script>

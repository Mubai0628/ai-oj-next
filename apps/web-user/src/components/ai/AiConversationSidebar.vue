<template>
  <aside class="ai-conversation-sidebar">
    <AiProblemContextCard
      :problem-id="problemId"
      :problem-title="problemTitle"
      :difficulty="problemDifficulty"
      :tags="problemTags"
    />

    <div class="ai-conversation-sidebar__header">
      <div>
        <strong>{{ t('aiAssistant.problemHistory') }}</strong>
        <span>{{ t('aiAssistant.problemHistoryCopy') }}</span>
      </div>
      <button type="button" @click="$emit('new')">{{ t('ai.newChat') }}</button>
    </div>

    <div class="ai-conversation-list">
      <EmptyState
        v-if="!conversations.length"
        :title="t('aiAssistant.noProblemHistoryTitle')"
        :description="t('aiAssistant.noProblemHistoryDescription')"
      >
        <button class="ai-empty-action" type="button" @click="$emit('new')">{{ t('aiAssistant.startQuestion') }}</button>
      </EmptyState>
      <AiConversationCard
        v-for="conversation in conversations"
        v-else
        :key="conversation.id"
        :conversation="conversation"
        :active="conversation.id === selectedId"
        @click="$emit('select', conversation.id)"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import AiConversationCard from '@/components/ai/AiConversationCard.vue';
import AiProblemContextCard from '@/components/ai/AiProblemContextCard.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import type { AiConversation } from '@/types/ai-assistant';

defineProps<{
  problemId?: string | number;
  problemTitle?: string;
  problemDifficulty?: string;
  problemTags?: string[];
  conversations: AiConversation[];
  selectedId?: string;
}>();

defineEmits<{
  select: [conversationId: string];
  new: [];
}>();

const { t } = useI18n();
</script>

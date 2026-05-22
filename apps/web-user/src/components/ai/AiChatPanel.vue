<template>
  <section class="ai-chat-panel">
    <header class="ai-chat-header">
      <div>
        <span>{{ conversation ? t('aiAssistant.selectedConversation') : t('aiAssistant.newConversation') }}</span>
        <h3>{{ conversation?.title || t('aiAssistant.newConversationTitle') }}</h3>
        <p>{{ contextLabel }}</p>
      </div>
      <StatusChip :label="sending ? t('ai.thinking') : t('common.ready')" :tone="sending ? 'primary' : 'success'" />
    </header>

    <div class="ai-chat-mode-row">
      <AiModeTabs :model-value="mode" @update:model-value="$emit('update:mode', $event)" />
      <p>{{ t('aiAssistant.rule') }}</p>
    </div>

    <AiMessageList
      :messages="conversation?.messages || []"
      :empty-title="emptyTitle"
      :empty-description="emptyDescription"
    >
      <template #empty>
        <AiQuickPromptChips :mode="mode" @pick="$emit('quick', $event)" />
      </template>
    </AiMessageList>

    <div v-if="error" class="ai-chat-error">
      <span>{{ error }}</span>
      <button type="button" @click="$emit('retry')">{{ t('problems.retry') }}</button>
    </div>

    <AiQuickPromptChips v-if="conversation?.messages.length" :mode="mode" @pick="$emit('quick', $event)" />

    <AiComposer
      :model-value="input"
      :sending="sending"
      :placeholder="t('aiAssistant.placeholder')"
      @update:model-value="$emit('update:input', $event)"
      @send="$emit('send')"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import AiComposer from '@/components/ai/AiComposer.vue';
import AiMessageList from '@/components/ai/AiMessageList.vue';
import AiModeTabs from '@/components/ai/AiModeTabs.vue';
import AiQuickPromptChips from '@/components/ai/AiQuickPromptChips.vue';
import StatusChip from '@/components/common/StatusChip.vue';
import type { AiConversation, AiMode } from '@/types/ai-assistant';

const props = defineProps<{
  conversation?: AiConversation;
  mode: AiMode;
  input: string;
  sending: boolean;
  error?: string;
  contextLabel: string;
  emptyTitle: string;
  emptyDescription: string;
}>();

defineEmits<{
  'update:mode': [value: AiMode];
  'update:input': [value: string];
  send: [];
  retry: [];
  quick: [value: string];
}>();

const { t } = useI18n();

const contextLabel = computed(() => props.contextLabel);
</script>

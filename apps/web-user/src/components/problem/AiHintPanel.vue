<template>
  <section class="ai-hint-card">
    <header class="ai-hint-card__header">
      <div>
        <h2 class="ai-hint-card__title">{{ t('problems.aiGuidance') }}</h2>
        <p>{{ t('problems.aiGuidanceCopy') }}</p>
      </div>
      <span class="ai-rule-chip">?</span>
    </header>

    <p class="ai-rule-text">{{ t('problems.aiRule') }}</p>

    <div class="ai-quick-actions">
      <button v-for="quick in quickPrompts" :key="quick" type="button" :disabled="loading" @click="$emit('quick', quick)">
        {{ quick }}
      </button>
    </div>

    <textarea
      class="ai-input"
      :value="prompt"
      :placeholder="t('problems.aiPlaceholder')"
      :disabled="loading"
      @input="$emit('update:prompt', ($event.target as HTMLTextAreaElement).value)"
    />

    <button class="workspace-button workspace-button--primary ai-hint-submit" type="button" :disabled="loading || !prompt.trim()" @click="$emit('ask')">
      {{ loading ? t('problems.waitingTutor') : t('problems.askGuidance') }}
    </button>

    <a-alert v-if="error" type="error" closable @close="$emit('clear-error')">
      {{ error }}
      <template #action>
        <a-button size="mini" type="text" @click="$emit('ask')">{{ t('problems.retry') }}</a-button>
      </template>
    </a-alert>

    <div v-if="answer || loading" class="ai-answer-box">
      <span v-if="loading && !answer">{{ t('problems.waitingTutor') }}</span>
      <p v-if="answer">{{ answer }}</p>
    </div>
    <div v-else class="ai-answer-empty">
      {{ t('problems.guidancePrompt') }}
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

defineProps<{
  prompt: string;
  answer: string;
  loading: boolean;
  error: string;
}>();

defineEmits<{
  'update:prompt': [value: string];
  ask: [];
  quick: [value: string];
  'clear-error': [];
}>();

const { t } = useI18n();

const quickPrompts = computed(() => [
  t('problems.quickIdea'),
  t('problems.quickAlgorithm'),
  t('problems.quickBoundary'),
  t('problems.quickOptimize')
]);
</script>

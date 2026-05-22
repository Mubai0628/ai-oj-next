<template>
  <section class="solve-card">
    <header class="solve-card__header">
      <div>
        <h2 class="solve-card__title">{{ t('problems.submitSolution') }}</h2>
        <p>{{ t('problems.codeStarter') }}</p>
      </div>
      <div class="solve-card__actions">
        <button class="ai-open-button" type="button" @click="$emit('open-ai')">{{ t('problems.openAiAssistant') }}</button>
        <a-select :model-value="language" :disabled="submitting" class="solve-language-select" @update:model-value="onLanguageChange">
          <a-option v-for="item in languages" :key="item.value" :value="item.value">{{ item.label }}</a-option>
        </a-select>
      </div>
    </header>

    <CodeEditorShell
      :model-value="code"
      :language-label="currentLanguageLabel"
      :cursor="cursor"
      :draft-status="draftStatus"
      :submitting="submitting"
      @update:model-value="$emit('update:code', $event)"
      @cursor-change="$emit('cursor-change', $event)"
      @reset="$emit('reset-code')"
    />

    <div class="submit-actions">
      <button class="workspace-button workspace-button--ghost" type="button" :disabled="submitting" @click="$emit('save-draft')">
        {{ t('problems.saveDraft') }}
      </button>
      <button class="workspace-button workspace-button--primary" type="button" :disabled="submitting" @click="$emit('submit')">
        {{ submitting ? t('problems.submitting') : t('problems.submit') }}
      </button>
    </div>

    <a-alert v-if="message" class="submit-feedback" :type="messageType" closable @close="$emit('clear-message')">
      {{ message }}
    </a-alert>

    <article v-if="submitResult?.id" class="submit-result-card">
      <div>
        <span>{{ t('problems.submitResult') }}</span>
        <strong>#{{ submitResult.id }}</strong>
      </div>
      <div v-if="submitResult.status">
        <span>{{ t('common.status') }}</span>
        <strong>{{ t(`submissionStatus.${submitResult.status}`) }}</strong>
      </div>
      <div>
        <span>{{ t('common.language') }}</span>
        <strong>{{ submitResult.language || currentLanguageLabel }}</strong>
      </div>
      <div v-if="submitResult.timeMillis !== undefined">
        <span>{{ t('common.time') }}</span>
        <strong>{{ submitResult.timeMillis }} ms</strong>
      </div>
      <div v-if="submitResult.memoryKb !== undefined">
        <span>{{ t('common.memory') }}</span>
        <strong>{{ submitResult.memoryKb }} KB</strong>
      </div>
      <router-link to="/submissions">{{ t('problems.viewSubmission') }}</router-link>
    </article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import CodeEditorShell from '@/components/problem/CodeEditorShell.vue';
import type { CodeLanguage, EditorCursorState, SubmitResultView } from '@/types/problem-workspace';

const props = defineProps<{
  languages: CodeLanguage[];
  language: string;
  code: string;
  cursor: EditorCursorState;
  draftStatus: string;
  submitting: boolean;
  message: string;
  messageType: 'success' | 'error' | 'info';
  submitResult?: SubmitResultView | null;
}>();

const emit = defineEmits<{
  'update:language': [value: string];
  'update:code': [value: string];
  'cursor-change': [value: EditorCursorState];
  submit: [];
  'save-draft': [];
  'reset-code': [];
  'clear-message': [];
  'open-ai': [];
}>();

const { t } = useI18n();

const currentLanguageLabel = computed(() => props.languages.find((item) => item.value === props.language)?.label ?? props.language);

function onLanguageChange(value: string | number | boolean | Record<string, unknown> | Array<string | number | boolean | Record<string, unknown>>) {
  if (typeof value === 'string') {
    emit('update:language', value);
  }
}
</script>

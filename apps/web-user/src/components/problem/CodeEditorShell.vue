<template>
  <div class="editor-shell">
    <div class="editor-toolbar">
      <button type="button" disabled :title="t('problems.runSampleUnavailable')">{{ t('problems.runSample') }}</button>
      <button type="button" :disabled="submitting" @click="$emit('reset')">{{ t('problems.resetCode') }}</button>
      <button type="button" disabled :title="t('problems.formatUnavailable')">{{ t('problems.formatCode') }}</button>
      <button type="button" disabled :title="t('problems.fullscreenUnavailable')">{{ t('problems.fullscreen') }}</button>
    </div>

    <div class="editor-body">
      <pre class="editor-lines" aria-hidden="true">{{ lineNumbers }}</pre>
      <textarea
        class="editor-area"
        :value="modelValue"
        spellcheck="false"
        :disabled="submitting"
        @input="onInput"
        @click="emitCursor"
        @keyup="emitCursor"
        @select="emitCursor"
      />
    </div>

    <footer class="editor-status">
      <span>{{ t('problems.cursorPosition', { line: cursor.line, column: cursor.column }) }}</span>
      <span>{{ t('problems.indentSpaces') }}</span>
      <span>{{ languageLabel }}</span>
      <span>{{ draftStatus }}</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { EditorCursorState } from '@/types/problem-workspace';

const props = defineProps<{
  modelValue: string;
  languageLabel: string;
  cursor: EditorCursorState;
  draftStatus: string;
  submitting: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'cursor-change': [value: EditorCursorState];
  reset: [];
}>();

const { t } = useI18n();

const lineNumbers = computed(() => {
  const count = Math.max(1, props.modelValue.split('\n').length);
  return Array.from({ length: count }, (_, index) => index + 1).join('\n');
});

function cursorState(textarea: HTMLTextAreaElement): EditorCursorState {
  const value = textarea.value.slice(0, textarea.selectionStart);
  const lines = value.split('\n');
  return {
    line: lines.length,
    column: (lines.at(-1)?.length ?? 0) + 1
  };
}

function emitCursor(event: Event) {
  emit('cursor-change', cursorState(event.target as HTMLTextAreaElement));
}

function onInput(event: Event) {
  const textarea = event.target as HTMLTextAreaElement;
  emit('update:modelValue', textarea.value);
  emit('cursor-change', cursorState(textarea));
}
</script>

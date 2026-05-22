<template>
  <aside class="editor-pane">
    <CodeSubmitPanel
      :languages="languages"
      :language="language"
      :code="code"
      :cursor="cursor"
      :draft-status="draftStatus"
      :submitting="submitting"
      :message="message"
      :message-type="messageType"
      :submit-result="submitResult"
      @update:language="$emit('update:language', $event)"
      @update:code="$emit('update:code', $event)"
      @cursor-change="$emit('cursor-change', $event)"
      @submit="$emit('submit')"
      @save-draft="$emit('save-draft')"
      @reset-code="$emit('reset-code')"
      @clear-message="$emit('clear-message')"
      @open-ai="$emit('open-ai')"
    />
  </aside>
</template>

<script setup lang="ts">
import CodeSubmitPanel from '@/components/problem/CodeSubmitPanel.vue';
import type { CodeLanguage, EditorCursorState, SubmitResultView } from '@/types/problem-workspace';

defineProps<{
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

defineEmits<{
  'update:language': [value: string];
  'update:code': [value: string];
  'cursor-change': [value: EditorCursorState];
  submit: [];
  'save-draft': [];
  'reset-code': [];
  'clear-message': [];
  'open-ai': [];
}>();
</script>

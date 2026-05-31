<template>
  <main class="problem-page">
    <ProblemHeader :problem="problem" :loading="loading" :problem-id="problemId" @refresh="loadProblem" />

    <a-alert v-if="error" type="error" closable @close="error = ''">
      {{ error }}
      <template #action>
        <a-button size="mini" type="text" @click="loadProblem">{{ t('problems.reload') }}</a-button>
        <router-link to="/problems" class="text-link">{{ t('problems.backToList') }}</router-link>
      </template>
    </a-alert>

    <LoadingSkeleton v-if="loading && !problem" />

    <EmptyState v-else-if="!problem" :title="t('problems.loadErrorTitle')" :description="t('problems.unavailable')">
      <router-link to="/problems" class="workspace-button workspace-button--ghost">{{ t('problems.backToList') }}</router-link>
    </EmptyState>

    <SplitPane v-else @reset="onSplitReset">
      <template #left>
        <ProblemPane
          :problem="problem"
          :active-tab="activeTab"
          @update:active-tab="activeTab = $event"
          @copy-sample="copySample"
          @view-submission="onViewSubmission"
        />
      </template>

      <template #right>
        <EditorPane
          :languages="languages"
          :language="selectedLanguage"
          :code="submission.code"
          :cursor="cursor"
          :draft-status="draftStatus"
          :submitting="submitting"
          :message="submitState.message"
          :message-type="submitState.type"
          :submit-result="submitResult"
          @update:language="requestLanguageChange"
          @update:code="submission.code = $event"
          @cursor-change="updateCursor"
          @submit="submitCode"
          @save-draft="saveDraft"
          @reset-code="confirmResetOpen = true"
          @clear-message="submitState.message = ''"
          @open-ai="aiDrawerOpen = true"
        />
      </template>
    </SplitPane>

    <AiAssistantWorkspaceDrawer
      v-model:open="aiDrawerOpen"
      v-model:selected-conversation-id="aiSelectedConversationId"
      v-model:mode="aiMode"
      v-model:input="aiPrompt"
      v-model:sending="aiSending"
      v-model:error="aiError"
      :problem="problem"
      :code="submission.code"
      :language="selectedLanguage"
    />

    <SubmissionDetailModal
      v-model:visible="submissionModalVisible"
      :submission-id="viewingSubmissionId"
    />

    <ConfirmDialog
      v-model:open="confirmLanguageOpen"
      :title="t('problems.switchLanguageTitle')"
      :description="t('problems.switchLanguageDescription')"
      :cancel-label="t('common.cancel')"
      :confirm-label="t('problems.confirmSwitch')"
      tone="primary"
      @confirm="confirmLanguageChange"
    />

    <ConfirmDialog
      v-model:open="confirmResetOpen"
      :title="t('problems.resetCodeTitle')"
      :description="t('problems.resetCodeDescription')"
      :cancel-label="t('common.cancel')"
      :confirm-label="t('problems.confirmReset')"
      tone="danger"
      @confirm="resetCode"
    />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { ApiError, api, type EntityId } from '@aioj/api-client';
import ConfirmDialog from '@/components/common/ConfirmDialog.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import AiAssistantWorkspaceDrawer from '@/components/ai/AiAssistantWorkspaceDrawer.vue';
import EditorPane from '@/components/problem/EditorPane.vue';
import LoadingSkeleton from '@/components/problem/LoadingSkeleton.vue';
import ProblemHeader from '@/components/problem/ProblemHeader.vue';
import ProblemPane from '@/components/problem/ProblemPane.vue';
import SplitPane from '@/components/problem/SplitPane.vue';
import SubmissionDetailModal from '@/components/submission/SubmissionDetailModal.vue';
import type { AiMode } from '@/types/ai-assistant';
import {
  adaptProblem,
  adaptSubmitResult,
  type CodeLanguage,
  type EditorCursorState,
  type ProblemDetailModel,
  type ProblemTabKey,
  type SubmitResultView
} from '@/types/problem-workspace';

const props = defineProps<{ id: string }>();

const { t } = useI18n();
const problemId = computed(() => props.id);
const problem = ref<ProblemDetailModel | null>(null);
const loading = ref(false);
const error = ref('');
const activeTab = ref<ProblemTabKey>('statement');
const submitting = ref(false);
const submitResult = ref<SubmitResultView | null>(null);
const submitState = reactive<{ type: 'success' | 'error' | 'info'; message: string }>({ type: 'info', message: '' });
const selectedLanguage = ref('java');
const pendingLanguage = ref('');
const confirmLanguageOpen = ref(false);
const confirmResetOpen = ref(false);
const draftStatus = ref('');
const cursor = reactive<EditorCursorState>({ line: 1, column: 1 });
const submission = reactive({ code: '' });
const aiDrawerOpen = ref(false);
const aiSelectedConversationId = ref<string>();
const aiMode = ref<AiMode>('hint');
const aiPrompt = ref('');
const aiSending = ref(false);
const aiError = ref('');
const submissionModalVisible = ref(false);
const viewingSubmissionId = ref<EntityId | null>(null);

function defaultStarter(language: string) {
  const comment = t('problems.starterComment');
  const starterCode: Record<string, string> = {
    java: [
      'public class Main {',
      '  public static void main(String[] args) {',
      `    // ${comment}`,
      '  }',
      '}'
    ].join('\n'),
    cpp: [
      '#include <bits/stdc++.h>',
      'using namespace std;',
      '',
      'int main() {',
      '  ios::sync_with_stdio(false);',
      '  cin.tie(nullptr);',
      `  // ${comment}`,
      '  return 0;',
      '}'
    ].join('\n'),
    python: [
      'import sys',
      '',
      'def main():',
      `    # ${comment}`,
      '    pass',
      '',
      'if __name__ == "__main__":',
      '    main()'
    ].join('\n')
  };
  return starterCode[language] ?? starterCode.java;
}

const languages = computed<CodeLanguage[]>(() => [
  { label: 'Java', value: 'java', template: defaultStarter('java') },
  { label: 'C++', value: 'cpp', template: defaultStarter('cpp') },
  { label: 'Python', value: 'python', template: defaultStarter('python') }
]);

function templateFor(language: string) {
  return languages.value.find((item) => item.value === language)?.template ?? defaultStarter(language);
}

function draftKey(language: string) {
  return `aioj.problemDraft:${problemId.value}:${language}`;
}

function loadDraft(language: string) {
  return window.localStorage.getItem(draftKey(language));
}

function restoreCode(language = selectedLanguage.value) {
  const draft = loadDraft(language);
  submission.code = draft || templateFor(language);
  draftStatus.value = draft ? t('problems.draftRestored') : t('problems.templateReady');
  updateCursor({ line: 1, column: 1 });
}

function saveDraft() {
  window.localStorage.setItem(draftKey(selectedLanguage.value), submission.code);
  draftStatus.value = t('problems.draftSaved');
  Message.success(t('problems.draftSaved'));
}

function canReplaceCode(language: string) {
  return !submission.code.trim() || submission.code === templateFor(language);
}

function applyLanguage(language: string) {
  selectedLanguage.value = language;
  restoreCode(language);
}

function requestLanguageChange(language: string) {
  if (language === selectedLanguage.value) return;
  if (canReplaceCode(selectedLanguage.value)) {
    applyLanguage(language);
    return;
  }
  pendingLanguage.value = language;
  confirmLanguageOpen.value = true;
}

function confirmLanguageChange() {
  if (pendingLanguage.value) {
    applyLanguage(pendingLanguage.value);
    pendingLanguage.value = '';
  }
  confirmLanguageOpen.value = false;
}

function resetCode() {
  submission.code = templateFor(selectedLanguage.value);
  draftStatus.value = t('problems.templateReady');
  confirmResetOpen.value = false;
  Message.success(t('problems.codeStarter'));
}

function updateCursor(value: EditorCursorState) {
  cursor.line = value.line;
  cursor.column = value.column;
}

async function copySample(value: string) {
  await navigator.clipboard.writeText(value);
  Message.success(t('problems.sampleCopied'));
}

function onSplitReset() {
  Message.success(t('problems.layoutReset'));
}

function onViewSubmission(id: EntityId) {
  viewingSubmissionId.value = id;
  submissionModalVisible.value = true;
}

async function loadProblem() {
  if (!problemId.value) return;
  loading.value = true;
  error.value = '';
  try {
    problem.value = adaptProblem(await api.problem(problemId.value));
    activeTab.value = 'statement';
    restoreCode();
    aiSelectedConversationId.value = undefined;
    aiError.value = '';
  } catch (err) {
    error.value = err instanceof ApiError ? err.userMessage : (err instanceof Error ? err.message : t('problems.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function submitCode() {
  if (!problem.value || !submission.code.trim()) {
    Message.warning(t('problems.writeCodeWarning'));
    return;
  }
  submitting.value = true;
  submitState.type = 'info';
  submitState.message = t('problems.submitting');
  submitResult.value = null;
  try {
    const result = await api.submit({
      problemId: problem.value.id,
      language: selectedLanguage.value,
      code: submission.code
    });
    submitResult.value = adaptSubmitResult(result);
    submitState.type = 'success';
    submitState.message = t('problems.submittedStatus', {
      id: result.id,
      status: t(`submissionStatus.${result.status}`)
    });
  } catch (err) {
    submitState.type = 'error';
    submitState.message = err instanceof ApiError ? err.userMessage : (err instanceof Error ? err.message : t('problems.submitFailed'));
  } finally {
    submitting.value = false;
  }
}

watch(() => props.id, loadProblem);

onMounted(() => {
  restoreCode();
  loadProblem();
});
</script>

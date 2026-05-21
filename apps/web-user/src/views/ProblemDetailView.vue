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

    <section v-else class="problem-workspace">
      <article class="problem-card">
        <div class="problem-card__intro">
          <span class="problem-id">#{{ problem.id }}</span>
          <div class="problem-card__intro-main">
            <h2>{{ problem.title }}</h2>
            <DifficultyChip :difficulty="problem.difficulty" />
          </div>
          <ProblemMetaChips :problem="problem" />
        </div>

        <ProblemTabs v-model="activeTab" />

        <section v-if="activeTab === 'statement'" class="problem-tab-panel">
          <div class="markdown-body problem-statement" v-html="statementHtml" />
        </section>

        <section v-else-if="activeTab === 'samples'" class="problem-tab-panel">
          <EmptyState v-if="!problem.samples.length" :description="t('problems.noSamples')" />
          <div v-else class="sample-list">
            <SampleCaseCard
              v-for="(sample, index) in problem.samples"
              :key="`${index}-${sample.input}-${sample.output}`"
              :sample="sample"
              :index="index + 1"
              @copy="copySample"
            />
          </div>
        </section>

        <section v-else-if="activeTab === 'notes'" class="problem-tab-panel">
          <div class="problem-section">
            <h3 class="problem-section__title">{{ t('problems.notesTitle') }}</h3>
            <p class="problem-section__content">{{ t('problems.notesCopy') }}</p>
          </div>
          <div class="problem-section">
            <h3 class="problem-section__title">{{ t('problems.limits') }}</h3>
            <div class="problem-note-grid">
              <span>{{ t('problems.timeLimit') }}<strong>{{ problem.timeLimitMillis }} ms</strong></span>
              <span>{{ t('problems.memoryLimit') }}<strong>{{ problem.memoryLimitMb }} MB</strong></span>
              <span>{{ t('common.created') }}<strong>{{ formatDate(problem.createdAt) }}</strong></span>
            </div>
          </div>
        </section>

        <section v-else class="problem-tab-panel">
          <EmptyState :title="t('problems.relatedEmptyTitle')" :description="t('problems.relatedEmptyDescription')" />
        </section>
      </article>

      <aside class="solve-panel">
        <CodeSubmitPanel
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
        />

        <AiHintPanel
          v-model:prompt="guidancePrompt"
          :answer="guidanceAnswer"
          :loading="guidanceLoading"
          :error="guidanceError"
          @ask="askGuidance"
          @quick="setQuickPrompt"
          @clear-error="guidanceError = ''"
        />
      </aside>
    </section>

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
import { api, streamAi } from '@aioj/api-client';
import ConfirmDialog from '@/components/common/ConfirmDialog.vue';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import AiHintPanel from '@/components/problem/AiHintPanel.vue';
import CodeSubmitPanel from '@/components/problem/CodeSubmitPanel.vue';
import LoadingSkeleton from '@/components/problem/LoadingSkeleton.vue';
import ProblemHeader from '@/components/problem/ProblemHeader.vue';
import ProblemMetaChips from '@/components/problem/ProblemMetaChips.vue';
import ProblemTabs from '@/components/problem/ProblemTabs.vue';
import SampleCaseCard from '@/components/problem/SampleCaseCard.vue';
import {
  adaptProblem,
  adaptSubmitResult,
  type CodeLanguage,
  type EditorCursorState,
  type ProblemDetailModel,
  type ProblemTabKey,
  type SubmitResultView
} from '@/types/problem-workspace';
import { renderMarkdownLite } from '@/utils/markdown';

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
const guidancePrompt = ref('');
const guidanceAnswer = ref('');
const guidanceError = ref('');
const guidanceLoading = ref(false);
const guidanceConversationId = ref<string>();

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

const statementHtml = computed(() => renderMarkdownLite(problem.value?.statement || ''));

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

function formatDate(value?: string) {
  if (!value) return '-';
  return new Date(value).toLocaleString();
}

async function copySample(value: string) {
  await navigator.clipboard.writeText(value);
  Message.success(t('problems.sampleCopied'));
}

async function loadProblem() {
  if (!problemId.value) return;
  loading.value = true;
  error.value = '';
  try {
    problem.value = adaptProblem(await api.problem(problemId.value));
    activeTab.value = 'statement';
    restoreCode();
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('problems.loadFailed');
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
    submitState.message = err instanceof Error ? err.message : t('problems.submitFailed');
  } finally {
    submitting.value = false;
  }
}

function captureConversationId(data: string) {
  try {
    const payload = JSON.parse(data) as { conversationId?: string };
    if (payload.conversationId) guidanceConversationId.value = payload.conversationId;
  } catch {
    if (data) guidanceConversationId.value = data;
  }
}

function setQuickPrompt(value: string) {
  guidancePrompt.value = value;
}

async function askGuidance() {
  if (!problem.value || !guidancePrompt.value.trim()) {
    Message.warning(t('ai.enterQuestion'));
    return;
  }
  guidanceLoading.value = true;
  guidanceAnswer.value = '';
  guidanceError.value = '';
  const message = t('problems.tutorMessage', {
    id: problem.value.id,
    title: problem.value.title,
    request: guidancePrompt.value.trim()
  });
  try {
    await streamAi({ conversationId: guidanceConversationId.value, problemId: problem.value.id, message }, (event, data) => {
      if (event === 'meta') captureConversationId(data);
      if (event === 'message') guidanceAnswer.value += data;
      if (event === 'error') guidanceError.value = data || t('problems.guidanceUnavailable');
    });
  } catch (err) {
    guidanceError.value = err instanceof Error ? err.message : t('problems.guidanceUnavailable');
  } finally {
    guidanceLoading.value = false;
  }
}

watch(() => props.id, loadProblem);

onMounted(() => {
  guidancePrompt.value = t('problems.guidancePrompt');
  restoreCode();
  loadProblem();
});
</script>

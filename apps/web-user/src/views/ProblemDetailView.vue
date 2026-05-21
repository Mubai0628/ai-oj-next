<template>
  <section class="page-stack">
    <PageHeader :eyebrow="t('problems.problemEyebrow', { id: problemId })" :title="problem?.title || t('problems.detailTitle')">
      <template #actions>
        <router-link to="/problems" class="text-link">{{ t('problems.backToList') }}</router-link>
        <a-button :loading="loading" @click="loadProblem">{{ t('common.refresh') }}</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>
    <a-spin :loading="loading" :tip="t('problems.loading')">
      <EmptyState v-if="!problem" :description="t('problems.unavailable')" />
      <section v-else class="detail-grid">
        <article class="statement-panel">
          <div class="section-title problem-title-line">
            <div>
              <span class="problem-id">#{{ problem.id }}</span>
              <h2>{{ problem.title }}</h2>
            </div>
            <StatusChip :label="difficultyLabel(problem.difficulty)" :status="problem.difficulty" />
          </div>
          <div class="meta-row limit-pills">
            <span>{{ problem.timeLimitMillis }} ms</span>
            <span>{{ Math.round(problem.memoryLimitKb / 1024) }} MB</span>
            <span v-if="problem.tags.length">{{ problem.tags.join(' / ') }}</span>
          </div>
          <div class="markdown-body" v-html="statementHtml" />

          <a-divider />
          <h3>{{ t('problems.samples') }}</h3>
          <EmptyState v-if="!problem.samples.length" :description="t('problems.noSamples')" />
          <div class="sample-list">
            <div v-for="(sample, index) in problem.samples" :key="`${index}-${sample.input}-${sample.expectedOutput}`" class="sample">
              <span>{{ t('problems.input') }}</span>
              <code>{{ sample.input || `(${t('problems.emptyValue')})` }}</code>
              <span>{{ t('problems.output') }}</span>
              <code>{{ sample.expectedOutput || `(${t('problems.emptyValue')})` }}</code>
            </div>
          </div>
        </article>

        <aside class="submit-panel sticky-panel">
          <h2>{{ t('problems.submitSolution') }}</h2>
          <a-select v-model="submission.language" class="full-width">
            <a-option value="java">Java</a-option>
            <a-option value="cpp">C++</a-option>
            <a-option value="python">Python</a-option>
          </a-select>
          <a-textarea v-model="submission.code" class="code-box" :auto-size="{ minRows: 14, maxRows: 24 }" />
          <a-button type="primary" long :loading="submitting" @click="submitCode">{{ t('problems.submit') }}</a-button>
          <a-alert v-if="submitState.message" :type="submitState.type" closable @close="submitState.message = ''">
            {{ submitState.message }}
          </a-alert>

          <a-divider />
          <h2>{{ t('problems.aiGuidance') }}</h2>
          <p class="muted">{{ t('problems.aiGuidanceCopy') }}</p>
          <a-textarea v-model="guidancePrompt" :auto-size="{ minRows: 3, maxRows: 5 }" />
          <a-button long :loading="guidanceLoading" @click="askGuidance">{{ t('problems.askGuidance') }}</a-button>
          <div v-if="guidanceAnswer || guidanceLoading" class="assistant-box">
            <span v-if="guidanceLoading && !guidanceAnswer">{{ t('problems.waitingTutor') }}</span>
            <p>{{ guidanceAnswer }}</p>
          </div>
        </aside>
      </section>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { api, streamAi, type Difficulty, type ProblemResponse } from '@aioj/api-client';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import StatusChip from '@/components/common/StatusChip.vue';
import { renderMarkdownLite } from '@/utils/markdown';

const props = defineProps<{ id: string }>();
const { t } = useI18n();
const problemId = computed(() => props.id);
const loading = ref(false);
const error = ref('');
const problem = ref<ProblemResponse | null>(null);
const submitting = ref(false);
const submitState = reactive<{ type: 'success' | 'error' | 'info'; message: string }>({ type: 'info', message: '' });
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

const submission = reactive({
  language: 'java',
  code: defaultStarter('java')
});
const guidancePrompt = ref(t('problems.guidancePrompt'));
const guidanceAnswer = ref('');
const guidanceLoading = ref(false);
const guidanceConversationId = ref<string>();

const statementHtml = computed(() => renderMarkdownLite(problem.value?.statement || ''));

function difficultyLabel(difficulty: Difficulty) {
  return t(`difficulty.${difficulty}`);
}

async function loadProblem() {
  if (!problemId.value) return;
  loading.value = true;
  error.value = '';
  try {
    problem.value = await api.problem(problemId.value);
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
  try {
    const result = await api.submit({
      problemId: problem.value.id,
      language: submission.language,
      code: submission.code
    });
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

async function askGuidance() {
  if (!problem.value || !guidancePrompt.value.trim()) return;
  guidanceLoading.value = true;
  guidanceAnswer.value = '';
  const message = t('problems.tutorMessage', {
    id: problem.value.id,
    title: problem.value.title,
    request: guidancePrompt.value.trim()
  });
  try {
    await streamAi({ conversationId: guidanceConversationId.value, problemId: problem.value.id, message }, (event, data) => {
      if (event === 'meta') captureConversationId(data);
      if (event === 'message') guidanceAnswer.value += data;
      if (event === 'error') guidanceAnswer.value += `\n${data}`;
    });
  } catch (err) {
    if (!guidanceAnswer.value.trim()) {
      guidanceAnswer.value = err instanceof Error ? err.message : t('problems.guidanceUnavailable');
    }
  } finally {
    guidanceLoading.value = false;
  }
}

watch(() => props.id, loadProblem);
watch(
  () => submission.language,
  (language, previous) => {
    if (!submission.code.trim() || submission.code === defaultStarter(previous)) {
      submission.code = defaultStarter(language);
    }
  }
);
onMounted(loadProblem);
</script>

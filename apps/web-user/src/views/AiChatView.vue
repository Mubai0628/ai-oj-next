<template>
  <section class="page-stack">
    <PageHeader :eyebrow="t('ai.eyebrow')" :title="t('ai.title')" :description="t('ai.tutorCopy')">
      <template #actions>
        <a-tag v-if="conversationId">{{ t('ai.conversation', { id: conversationId.slice(0, 8) }) }}</a-tag>
        <a-button @click="newConversation">{{ t('ai.newChat') }}</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>

    <section class="chat-layout">
      <aside class="chat-side">
        <div class="chat-side__title">
          <h2>{{ t('ai.context') }}</h2>
          <a-button size="small" @click="newConversation">{{ t('ai.newChat') }}</a-button>
        </div>
        <p class="muted">{{ t('ai.contextCopy') }}</p>
        <a-select v-model="problemIdText" allow-clear :placeholder="t('ai.attachProblem')" class="full-width">
          <a-option value="">{{ t('ai.noProblem') }}</a-option>
          <a-option v-for="problem in problems" :key="problem.id" :value="String(problem.id)">
            #{{ problem.id }} {{ problem.title }}
          </a-option>
        </a-select>

        <div class="conversation-list">
          <div class="conversation-item active">
            <strong>{{ selectedProblem?.title || t('ai.currentSession') }}</strong>
            <span>{{ selectedProblem ? preview(selectedProblem.statement) : t('ai.tutorCopy') }}</span>
          </div>
        </div>

        <h2>{{ t('ai.history') }}</h2>
        <div class="conversation-list">
          <button class="conversation-item" type="button" @click="newConversation">
            <strong>{{ t('ai.newChat') }}</strong>
            <span>{{ t('ai.historyEmpty') }}</span>
          </button>
        </div>
      </aside>

      <section class="chat-main">
        <div class="chat-main-header">
          <div>
            <h2>{{ t('ai.title') }}</h2>
            <p class="muted">{{ selectedProblem ? selectedProblem.title : t('ai.noProblem') }}</p>
          </div>
          <StatusChip :label="waiting ? t('ai.thinking') : t('common.ready')" :tone="waiting ? 'primary' : 'success'" />
        </div>

        <div class="chat-log">
          <EmptyState v-if="!messages.length" :title="t('ai.emptyTitle')" :description="t('ai.empty')">
            <div class="quick-prompts">
              <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="useQuickPrompt(prompt)">
                {{ prompt }}
              </button>
            </div>
          </EmptyState>
          <div v-for="(message, index) in messages" :key="index" class="chat-message" :class="message.role">
            <strong>{{ message.role === 'user' ? t('ai.you') : t('ai.tutor') }}</strong>
            <p v-if="message.content">{{ message.content }}</p>
            <p v-else class="muted">{{ streaming ? t('ai.streaming') : t('ai.thinking') }}</p>
          </div>
        </div>

        <div class="composer-shell">
          <p>{{ t('ai.inputNotice') }}</p>
          <div class="composer">
          <a-textarea
            ref="inputRef"
            v-model="input"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            :placeholder="t('ai.placeholder')"
            @keydown="handleInputKeydown"
          />
          <a-button class="send-button" type="primary" :loading="waiting" @click="send">{{ t('ai.send') }}</a-button>
          </div>
        </div>
      </section>

      <aside class="chat-recommend">
        <h2>{{ t('ai.relatedProblems') }}</h2>
        <p class="muted">{{ t('ai.relatedCopy') }}</p>
        <div class="recommend-list">
          <router-link v-for="problem in relatedProblems" :key="problem.id" class="recommend-item recommend-item--rich" :to="`/problems/${problem.id}`">
            <strong>{{ problem.title }}</strong>
            <span>{{ preview(problem.statement) }}</span>
            <DifficultyChip :difficulty="problem.difficulty" />
          </router-link>
        </div>
      </aside>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRoute } from 'vue-router';
import { api, streamAi, type ProblemResponse } from '@aioj/api-client';
import DifficultyChip from '@/components/common/DifficultyChip.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import StatusChip from '@/components/common/StatusChip.vue';

const route = useRoute();
const { t } = useI18n();
const problems = ref<ProblemResponse[]>([]);
const problemIdText = ref(typeof route.query.problemId === 'string' ? route.query.problemId : '');
const conversationId = ref<string>();
const input = ref('');
const error = ref('');
const waiting = ref(false);
const streaming = ref(false);
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([]);
const inputRef = ref<{ focus?: () => void } | null>(null);

const selectedProblem = computed(() => problems.value.find((problem) => String(problem.id) === problemIdText.value));
const quickPrompts = computed(() => [
  t('ai.quickStart'),
  t('ai.quickBoundary'),
  t('ai.quickWa'),
  t('ai.quickHintOnly')
]);
const relatedProblems = computed(() => {
  const selected = selectedProblem.value;
  const candidates = problems.value.filter((problem) => !selected || problem.id !== selected.id);
  if (!selected?.tags?.length) return candidates.slice(0, 5);
  return [...candidates]
    .sort((left, right) => tagOverlap(right, selected) - tagOverlap(left, selected))
    .slice(0, 5);
});

function preview(statement: string) {
  return statement.replace(/[#*_`]/g, '').replace(/\s+/g, ' ').slice(0, 82) || t('problems.openDetailFallback');
}

function tagOverlap(problem: ProblemResponse, selected: ProblemResponse) {
  const selectedTags = new Set(selected.tags);
  return problem.tags.filter((tag) => selectedTags.has(tag)).length;
}

function newConversation() {
  conversationId.value = undefined;
  messages.value = [];
  input.value = '';
  error.value = '';
}

async function useQuickPrompt(prompt: string) {
  input.value = prompt;
  await nextTick();
  inputRef.value?.focus?.();
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    void send();
  }
}

function captureConversationId(data: string) {
  try {
    const payload = JSON.parse(data) as { conversationId?: string };
    if (payload.conversationId) conversationId.value = payload.conversationId;
  } catch {
    if (data) conversationId.value = data;
  }
}

async function send() {
  const text = input.value.trim();
  if (!text) {
    Message.warning(t('ai.enterQuestion'));
    return;
  }
  const problemId = problemIdText.value || undefined;
  messages.value.push({ role: 'user', content: text });
  const assistant = { role: 'assistant' as const, content: '' };
  messages.value.push(assistant);
  input.value = '';
  error.value = '';
  waiting.value = true;
  streaming.value = false;
  const tutoringMessage = problemId
    ? t('ai.tutorWithProblem', { problemId, text })
    : t('ai.tutorGeneral', { text });

  try {
    await streamAi({ conversationId: conversationId.value, problemId, message: tutoringMessage }, (event, data) => {
      if (event === 'meta') captureConversationId(data);
      if (event === 'message') {
        streaming.value = true;
        assistant.content += data;
      }
      if (event === 'error' && !assistant.content) {
        error.value = data || t('ai.streamError');
      }
      if (event === 'done') {
        streaming.value = false;
      }
    });
  } catch (err) {
    if (!assistant.content.trim()) {
      error.value = err instanceof Error ? err.message : t('ai.chatFailed');
      assistant.content = error.value;
    }
  } finally {
    waiting.value = false;
    streaming.value = false;
  }
}

async function loadProblems() {
  try {
    const page = await api.problems({ page: 1, pageSize: 100 });
    problems.value = page.records;
  } catch {
    problems.value = [];
  }
}

onMounted(loadProblems);
</script>

<template>
  <section class="page-stack ai-history-page">
    <PageHeader :eyebrow="t('ai.eyebrow')" :title="t('ai.title')" :description="t('aiAssistant.historyPageCopy')">
      <template #actions>
        <a-button @click="newConversation">{{ t('ai.newChat') }}</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" closable @close="error = ''">{{ error }}</a-alert>

    <section class="ai-tutor-workspace">
      <aside class="ai-tutor-list-panel">
        <div class="ai-history-filter">
          <div class="ai-history-filter__field">
            <span class="ai-history-filter__label">{{ t('aiAssistant.problemFilter') }}</span>
            <a-select
              v-model="problemFilter"
              class="full-width"
              @change="onProblemFilterChange"
              @popup-visible-change="onProblemFilterPopupChange"
            >
              <a-option value="__all__">{{ t('aiAssistant.allProblems') }}</a-option>
              <a-option value="__none__">{{ t('aiAssistant.unlinkedProblem') }}</a-option>
              <a-option v-for="filter in linkedProblemFilters" :key="filter.problemId" :value="String(filter.problemId)">
                {{ filter.problemTitle || `#${filter.problemId}` }} · {{ t('aiAssistant.conversationCount', { count: filter.count }) }}
              </a-option>
            </a-select>
          </div>

          <div class="ai-history-filter__field">
            <span class="ai-history-filter__label">{{ t('aiAssistant.modeFilter') }}</span>
            <a-select
              v-model="modeFilter"
              class="full-width"
              @change="onModeFilterChange"
              @popup-visible-change="onModeFilterPopupChange"
            >
              <a-option value="all">{{ t('common.all') }}</a-option>
              <a-option value="hint">{{ t('aiAssistant.modes.hint') }}</a-option>
              <a-option value="debug">{{ t('aiAssistant.modes.debug') }}</a-option>
              <a-option value="edge">{{ t('aiAssistant.modes.edge') }}</a-option>
              <a-option value="optimize">{{ t('aiAssistant.modes.optimize') }}</a-option>
            </a-select>
          </div>

          <a-input v-model="keyword" :placeholder="t('aiAssistant.searchPlaceholder')" allow-clear />
          <a-button @click="resetFilters">{{ t('submissions.resetFilters') }}</a-button>
        </div>

        <div class="ai-history-list">
          <EmptyState
            v-if="!assistant.sortedConversations.length"
            :title="t('aiAssistant.allHistoryEmptyTitle')"
            :description="t('aiAssistant.allHistoryEmptyDescription')"
          />
          <EmptyState
            v-else-if="!filteredConversations.length"
            :title="emptyListTitle"
            :description="t('aiAssistant.noSearchDescription')"
          >
            <button class="ai-empty-action" type="button" @click="resetFilters">{{ t('submissions.resetFilters') }}</button>
          </EmptyState>
          <AiConversationCard
            v-for="conversation in filteredConversations"
            v-else
            :key="conversation.id"
            :conversation="conversation"
            :active="conversation.id === selectedConversationId"
            @click="selectConversation(conversation.id)"
          />
        </div>
      </aside>

      <section class="ai-tutor-detail-panel">
        <AiProblemContextCard
          v-if="detailProblemId || detailProblemTitle"
          :problem-id="detailProblemId"
          :problem-title="detailProblemTitle"
          :difficulty="selectedConversation?.problemDifficulty"
          :tags="selectedConversation?.problemTags"
        />

        <AiChatPanel
          :conversation="selectedConversation"
          :mode="mode"
          :input="input"
          :sending="sending"
          :error="error"
          :context-label="detailProblemTitle || t('ai.noProblem')"
          :empty-title="emptyDetailTitle"
          :empty-description="emptyDetailDescription"
          @update:mode="setMode"
          @update:input="input = $event"
          @send="send"
          @retry="retryLast"
          @quick="setQuickPrompt"
        />
      </section>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRoute, useRouter } from 'vue-router';
import { api, streamAi, type ProblemResponse } from '@aioj/api-client';
import AiChatPanel from '@/components/ai/AiChatPanel.vue';
import AiConversationCard from '@/components/ai/AiConversationCard.vue';
import AiProblemContextCard from '@/components/ai/AiProblemContextCard.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import PageHeader from '@/components/common/PageHeader.vue';
import { useAiAssistantStore } from '@/stores/aiAssistant';
import type { AiConversation, AiMode } from '@/types/ai-assistant';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const assistant = useAiAssistantStore();

const problems = ref<ProblemResponse[]>([]);
const problemFilter = ref('__all__');
const modeFilter = ref<AiMode | 'all'>('all');
const keyword = ref('');
const selectedConversationId = ref<string>();
const input = ref('');
const mode = ref<AiMode>('hint');
const sending = ref(false);
const error = ref('');
const isApplyingRouteQuery = ref(false);
const problemFilterPopupVisible = ref(false);
const modeFilterPopupVisible = ref(false);
let querySyncTimer: ReturnType<typeof setTimeout> | undefined;

const linkedProblemFilters = computed(() => assistant.problemFilters.filter((filter) => filter.problemId !== undefined));
const selectedConversation = computed(() => assistant.getConversation(selectedConversationId.value));
const selectedFilterProblem = computed(() => problems.value.find((problem) => String(problem.id) === problemFilter.value));
const detailProblemId = computed(() => selectedConversation.value?.problemId || selectedFilterProblem.value?.id);
const detailProblemTitle = computed(() => selectedConversation.value?.problemTitle || selectedFilterProblem.value?.title);

const filteredConversations = computed(() => {
  const keywordText = keyword.value.trim().toLowerCase();
  return assistant.sortedConversations.filter((conversation) => {
    if (problemFilter.value === '__none__' && conversation.problemId !== undefined) return false;
    if (problemFilter.value !== '__all__' && problemFilter.value !== '__none__' && String(conversation.problemId) !== problemFilter.value) {
      return false;
    }
    if (modeFilter.value !== 'all' && conversation.mode !== modeFilter.value) return false;
    if (!keywordText) return true;
    const haystack = [
      conversation.title,
      conversation.problemTitle,
      ...conversation.messages.map((message) => message.content)
    ].join(' ').toLowerCase();
    return haystack.includes(keywordText);
  });
});

const emptyListTitle = computed(() => {
  if (keyword.value.trim()) return t('aiAssistant.noSearchTitle');
  if (problemFilter.value !== '__all__') return t('aiAssistant.noProblemHistoryTitle');
  return t('aiAssistant.noSearchTitle');
});
const emptyDetailTitle = computed(() => {
  if (selectedConversation.value) return t('aiAssistant.emptyConversation');
  if (problemFilter.value !== '__all__') return t('aiAssistant.noProblemHistoryTitle');
  return t('aiAssistant.allHistoryEmptyTitle');
});
const emptyDetailDescription = computed(() => {
  if (selectedConversation.value) return t('aiAssistant.emptyConversationDescription');
  if (problemFilter.value !== '__all__') return t('aiAssistant.noProblemHistoryDescription');
  return t('aiAssistant.allHistoryEmptyDescription');
});

function makeTitle(text: string) {
  return text.replace(/\s+/g, ' ').trim().slice(0, 24) || t('aiAssistant.newConversationTitle');
}

function problemContext() {
  if (selectedConversation.value?.problemId) {
    return {
      problemId: selectedConversation.value.problemId,
      problemTitle: selectedConversation.value.problemTitle,
      problemDifficulty: selectedConversation.value.problemDifficulty,
      problemTags: selectedConversation.value.problemTags
    };
  }
  if (problemFilter.value !== '__all__' && problemFilter.value !== '__none__') {
    const problem = selectedFilterProblem.value;
    return {
      problemId: problemFilter.value,
      problemTitle: problem?.title,
      problemDifficulty: problem?.difficulty,
      problemTags: problem?.tags
    };
  }
  return {};
}

function ensureConversation(text: string): AiConversation {
  if (selectedConversation.value) return selectedConversation.value;
  const context = problemContext();
  const conversation = assistant.createConversation({
    ...context,
    source: 'ai_tutor',
    mode: mode.value,
    title: makeTitle(text)
  });
  selectedConversationId.value = conversation.id;
  syncQuery();
  return conversation;
}

function buildMessage(text: string, conversation: AiConversation) {
  if (conversation.problemId) {
    return t('aiAssistant.problemTutorMessage', {
      id: conversation.problemId,
      title: conversation.problemTitle || '',
      mode: t(`aiAssistant.modes.${mode.value}`),
      request: text
    });
  }
  return t('ai.tutorGeneral', { text });
}

function captureRemoteConversationId(conversationId: string, data: string) {
  try {
    const payload = JSON.parse(data) as { conversationId?: string };
    if (payload.conversationId) assistant.updateConversation(conversationId, { remoteConversationId: payload.conversationId });
  } catch {
    if (data) assistant.updateConversation(conversationId, { remoteConversationId: data });
  }
}

function selectConversation(conversationId: string) {
  selectedConversationId.value = conversationId;
  const conversation = assistant.getConversation(conversationId);
  if (conversation) mode.value = conversation.mode;
  syncQuery();
}

function newConversation() {
  selectedConversationId.value = undefined;
  input.value = '';
  error.value = '';
  syncQuery();
}

function normalizeProblemFilter(value: unknown) {
  return typeof value === 'string' && value ? value : '__all__';
}

function normalizeModeFilter(value: unknown): AiMode | 'all' {
  return value === 'hint' || value === 'debug' || value === 'edge' || value === 'optimize' ? value : 'all';
}

function scheduleQuerySync() {
  if (isApplyingRouteQuery.value) return;
  if (querySyncTimer) clearTimeout(querySyncTimer);
  querySyncTimer = setTimeout(() => {
    void nextTick(() => {
      if (problemFilterPopupVisible.value || modeFilterPopupVisible.value) {
        scheduleQuerySync();
        return;
      }
      syncQuery();
    });
  }, 120);
}

function onProblemFilterChange(value: unknown) {
  problemFilter.value = normalizeProblemFilter(value);
  ensureSelection();
  scheduleQuerySync();
}

function onModeFilterChange(value: unknown) {
  modeFilter.value = normalizeModeFilter(value);
  ensureSelection();
  scheduleQuerySync();
}

function onProblemFilterPopupChange(visible: boolean) {
  problemFilterPopupVisible.value = visible;
  if (!visible) scheduleQuerySync();
}

function onModeFilterPopupChange(visible: boolean) {
  modeFilterPopupVisible.value = visible;
  if (!visible) scheduleQuerySync();
}

function setMode(value: AiMode) {
  mode.value = value;
  if (selectedConversationId.value) assistant.updateConversation(selectedConversationId.value, { mode: value });
}

function setQuickPrompt(value: string) {
  input.value = value;
}

async function send() {
  const text = input.value.trim();
  if (!text || sending.value) {
    if (!text) Message.warning(t('ai.enterQuestion'));
    return;
  }
  const conversation = ensureConversation(text);
  input.value = '';
  error.value = '';
  sending.value = true;

  assistant.appendMessage(conversation.id, {
    problemId: conversation.problemId,
    role: 'user',
    content: text,
    status: 'success'
  });
  const assistantMessage = assistant.appendMessage(conversation.id, {
    problemId: conversation.problemId,
    role: 'assistant',
    content: '',
    status: 'sending'
  });
  let content = '';

  try {
    await streamAi(
      {
        conversationId: assistant.getConversation(conversation.id)?.remoteConversationId,
        problemId: conversation.problemId,
        message: buildMessage(text, conversation)
      },
      (event, data) => {
        if (event === 'meta') captureRemoteConversationId(conversation.id, data);
        if (event === 'message' && assistantMessage) {
          content += data;
          assistant.updateMessage(conversation.id, assistantMessage.id, { content, status: 'sending' });
        }
        if (event === 'error' && assistantMessage) {
          error.value = data || t('ai.streamError');
          assistant.updateMessage(conversation.id, assistantMessage.id, {
            content: content || error.value,
            status: 'error'
          });
        }
        if (event === 'done' && assistantMessage) {
          assistant.updateMessage(conversation.id, assistantMessage.id, { content, status: 'success' });
        }
      }
    );
    if (assistantMessage && content) {
      assistant.updateMessage(conversation.id, assistantMessage.id, { content, status: 'success' });
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : t('ai.chatFailed');
    if (assistantMessage) {
      assistant.updateMessage(conversation.id, assistantMessage.id, {
        content: content || error.value,
        status: 'error'
      });
    }
  } finally {
    sending.value = false;
  }
}

function retryLast() {
  const latestUserMessage = [...(selectedConversation.value?.messages || [])]
    .reverse()
    .find((message) => message.role === 'user');
  if (latestUserMessage) {
    input.value = latestUserMessage.content;
    void send();
  }
}

function resetFilters() {
  problemFilter.value = '__all__';
  modeFilter.value = 'all';
  keyword.value = '';
  ensureSelection();
  scheduleQuerySync();
}

function syncQuery() {
  const query: Record<string, string> = {};
  if (problemFilter.value !== '__all__') query.problemId = problemFilter.value === '__none__' ? '__none__' : problemFilter.value;
  if (selectedConversationId.value) query.conversationId = selectedConversationId.value;
  const currentProblemId = typeof route.query.problemId === 'string' ? route.query.problemId : undefined;
  const currentConversationId = typeof route.query.conversationId === 'string' ? route.query.conversationId : undefined;
  if (currentProblemId === query.problemId && currentConversationId === query.conversationId) return;
  void router.replace({ query });
}

function applyQuery() {
  isApplyingRouteQuery.value = true;
  const queryProblemId = typeof route.query.problemId === 'string' ? route.query.problemId : '';
  const queryConversationId = typeof route.query.conversationId === 'string' ? route.query.conversationId : '';
  problemFilter.value = queryProblemId || '__all__';
  selectedConversationId.value = queryConversationId || undefined;
  ensureSelection();
  void nextTick(() => {
    isApplyingRouteQuery.value = false;
  });
}

function ensureSelection() {
  if (selectedConversationId.value && filteredConversations.value.some((conversation) => conversation.id === selectedConversationId.value)) {
    const conversation = assistant.getConversation(selectedConversationId.value);
    if (conversation) mode.value = conversation.mode;
    return;
  }
  const latest = filteredConversations.value[0];
  selectedConversationId.value = latest?.id;
  mode.value = latest?.mode || 'hint';
}

async function loadProblems() {
  try {
    const page = await api.problems({ page: 1, pageSize: 100 });
    problems.value = page.records;
  } catch {
    problems.value = [];
  }
}

watch(keyword, () => {
  ensureSelection();
});

watch(
  () => route.query,
  () => {
    if (problemFilterPopupVisible.value || modeFilterPopupVisible.value) return;
    applyQuery();
  }
);

onMounted(async () => {
  assistant.load();
  await loadProblems();
  applyQuery();
});

onBeforeUnmount(() => {
  if (querySyncTimer) clearTimeout(querySyncTimer);
});
</script>

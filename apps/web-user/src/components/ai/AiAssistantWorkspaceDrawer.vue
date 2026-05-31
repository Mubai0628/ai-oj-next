<template>
  <Teleport to="body">
    <Transition name="ai-workspace-fade">
      <div v-if="open" class="ai-workspace-overlay ai-assist-overlay" role="presentation" @click.self="close">
        <Transition name="ai-workspace-slide" appear>
          <aside class="ai-workspace-drawer ai-assist-modal" role="dialog" aria-modal="true" :aria-label="t('aiAssistant.title')">
            <header class="ai-workspace-header">
              <div>
                <span>{{ t('aiAssistant.eyebrow') }}</span>
                <h2>{{ t('aiAssistant.title') }}</h2>
                <p>{{ t('aiAssistant.subtitle') }}</p>
              </div>
              <div class="ai-workspace-header__actions">
                <button class="no-wrap-button" type="button" @click="viewAllHistory">{{ t('aiAssistant.viewAllHistory') }}</button>
                <button class="ai-workspace-close" type="button" :aria-label="t('common.cancel')" @click="close">×</button>
              </div>
            </header>

            <div class="ai-workspace-body">
              <AiConversationSidebar
                :problem-id="problem?.id"
                :problem-title="problem?.title"
                :problem-difficulty="problem?.difficulty"
                :problem-tags="problem?.tags"
                :conversations="problemConversations"
                :selected-id="selectedConversationId"
                @select="selectConversation"
                @new="newConversation"
              />

              <AiChatPanel
                :conversation="selectedConversation"
                :mode="mode"
                :input="input"
                :sending="sending"
                :error="error"
                :context-label="contextLabel"
                :context-scope-label="contextScopeLabel"
                :context-notice="contextNotice"
                :empty-title="t('aiAssistant.noProblemHistoryTitle')"
                :empty-description="t('aiAssistant.noProblemHistoryDescription')"
                rich-markdown
                @update:mode="setMode"
                @update:input="input = $event"
                @send="send"
                @retry="retryLast"
                @quick="setQuickPrompt"
              />
            </div>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { useRouter } from 'vue-router';
import { streamAi, type AiChatPayload } from '@aioj/api-client';
import AiChatPanel from '@/components/ai/AiChatPanel.vue';
import AiConversationSidebar from '@/components/ai/AiConversationSidebar.vue';
import { useAiAssistantStore } from '@/stores/aiAssistant';
import type { AiConversation, AiMode } from '@/types/ai-assistant';
import type { ProblemDetailModel } from '@/types/problem-workspace';

const props = defineProps<{
  open: boolean;
  problem: ProblemDetailModel | null;
  code?: string;
  language?: string;
}>();

const emit = defineEmits<{
  'update:open': [value: boolean];
}>();

const { t } = useI18n();
const router = useRouter();
const assistant = useAiAssistantStore();

const selectedConversationId = defineModel<string | undefined>('selectedConversationId', { default: undefined });
const mode = defineModel<AiMode>('mode', { default: 'hint' });
const input = defineModel<string>('input', { default: '' });
const sending = defineModel<boolean>('sending', { default: false });
const error = defineModel<string>('error', { default: '' });

const problemId = computed(() => (props.problem ? String(props.problem.id) : undefined));
const problemConversations = computed(() => (problemId.value ? assistant.listByProblem(problemId.value) : []));
const selectedConversation = computed(() => assistant.getConversation(selectedConversationId.value));
const contextLabel = computed(() => props.problem?.title || t('ai.noProblem'));
const codeModes = new Set<AiMode>(['debug', 'edge', 'optimize']);
const usesCodeContext = computed(() => codeModes.has(mode.value));
const trimmedCode = computed(() => props.code?.trim() || '');
const contextScopeLabel = computed(() => (
  usesCodeContext.value && trimmedCode.value
    ? t('aiAssistant.contextProblemAndCode')
    : t('aiAssistant.contextProblemOnly')
));
const contextNotice = computed(() => {
  if (!usesCodeContext.value) return t('aiAssistant.contextProblemOnlyHint');
  if (trimmedCode.value) return t('aiAssistant.contextCodeIncluded');
  return t('aiAssistant.contextCodeMissing');
});

function promptKey() {
  return problemId.value ? `ai-oj:problem-ai-prompt:${problemId.value}` : 'ai-oj:problem-ai-prompt:general';
}

function close() {
  emit('update:open', false);
}

function selectLatestForProblem() {
  assistant.ensureLoaded();
  const latest = problemConversations.value[0];
  selectedConversationId.value = latest?.id;
  mode.value = latest?.mode || 'hint';
}

function selectConversation(conversationId: string) {
  selectedConversationId.value = conversationId;
  const conversation = assistant.getConversation(conversationId);
  if (conversation) mode.value = conversation.mode;
}

function newConversation() {
  selectedConversationId.value = undefined;
  input.value = '';
  error.value = '';
}

function setMode(value: AiMode) {
  mode.value = value;
  if (selectedConversationId.value) {
    assistant.updateConversation(selectedConversationId.value, { mode: value });
  }
}

function setQuickPrompt(value: string) {
  input.value = value;
}

function makeTitle(text: string) {
  const title = text.replace(/\s+/g, ' ').trim().slice(0, 24);
  return title || t('aiAssistant.newConversationTitle');
}

function captureRemoteConversationId(conversationId: string, data: string) {
  try {
    const payload = JSON.parse(data) as { conversationId?: string };
    if (payload.conversationId) assistant.updateConversation(conversationId, { remoteConversationId: payload.conversationId });
  } catch {
    if (data) assistant.updateConversation(conversationId, { remoteConversationId: data });
  }
}

function ensureConversation(text: string): AiConversation | undefined {
  if (selectedConversation.value) return selectedConversation.value;
  if (!props.problem) return undefined;
  const conversation = assistant.createConversation({
    problemId: props.problem.id,
    problemTitle: props.problem.title,
    problemDifficulty: props.problem.difficulty,
    problemTags: props.problem.tags,
    source: 'problem_detail',
    mode: mode.value,
    title: makeTitle(text)
  });
  selectedConversationId.value = conversation.id;
  return conversation;
}

function buildProblemContext(): AiChatPayload['problemContext'] | undefined {
  if (!props.problem) return undefined;
  return {
    id: props.problem.id,
    title: props.problem.title,
    difficulty: props.problem.difficulty,
    statement: props.problem.statement,
    notes: props.problem.notes,
    tags: props.problem.tags,
    samples: props.problem.samples.slice(0, 3).map((sample) => ({
      input: sample.input,
      expectedOutput: sample.output,
      sample: true
    })),
    timeLimitMillis: props.problem.timeLimitMillis,
    memoryLimitKb: props.problem.memoryLimitMb * 1024
  };
}

function buildCodeContext(): AiChatPayload['codeContext'] | undefined {
  if (!usesCodeContext.value || !trimmedCode.value) return undefined;
  return {
    language: props.language,
    code: trimmedCode.value
  };
}

function buildAiPayload(text: string, conversation: AiConversation): AiChatPayload {
  return {
    conversationId: assistant.getConversation(conversation.id)?.remoteConversationId,
    problemId: props.problem?.id,
    message: text,
    mode: mode.value,
    problemContext: buildProblemContext(),
    codeContext: buildCodeContext()
  };
}

async function send() {
  const text = input.value.trim();
  if (!props.problem || !text || sending.value) {
    if (!text) Message.warning(t('ai.enterQuestion'));
    return;
  }

  const conversation = ensureConversation(text);
  if (!conversation) return;
  input.value = '';
  error.value = '';
  sending.value = true;

  assistant.appendMessage(conversation.id, {
    problemId: props.problem.id,
    role: 'user',
    content: text,
    status: 'success'
  });
  const assistantMessage = assistant.appendMessage(conversation.id, {
    problemId: props.problem.id,
    role: 'assistant',
    content: '',
    status: 'sending'
  });
  let content = '';

  try {
    await streamAi(
      buildAiPayload(text, conversation),
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

function viewAllHistory() {
  const query: Record<string, string> = {};
  if (problemId.value) query.problemId = problemId.value;
  if (selectedConversationId.value) query.conversationId = selectedConversationId.value;
  close();
  void router.push({ name: 'ai-tutor', query });
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') close();
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      assistant.load();
      selectLatestForProblem();
      input.value = window.localStorage.getItem(promptKey()) || input.value;
      window.addEventListener('keydown', onKeydown);
    } else {
      window.removeEventListener('keydown', onKeydown);
    }
  },
  { immediate: true }
);

watch(problemId, () => {
  if (props.open) {
    selectLatestForProblem();
    input.value = window.localStorage.getItem(promptKey()) || '';
    error.value = '';
  }
});

watch(selectedConversation, (conversation) => {
  if (conversation) mode.value = conversation.mode;
});

watch(input, (value) => {
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(promptKey(), value);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown);
});
</script>

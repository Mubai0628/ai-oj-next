import type {
  AiConversation,
  AiConversationPatch,
  AiMessage,
  AiMessagePatch,
  AiProblemFilter,
  CreateAiConversationPayload
} from '@/types/ai-assistant';

const STORAGE_KEY = 'ai-oj:ai-conversations';

function now() {
  return Date.now();
}

export function createLocalId(prefix: string) {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `${prefix}_${crypto.randomUUID()}`;
  }
  return `${prefix}_${now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function normalizeConversations(value: unknown): AiConversation[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is AiConversation => Boolean(item && typeof item === 'object' && 'id' in item))
    .map((item) => ({
      ...item,
      messages: Array.isArray(item.messages) ? item.messages : [],
      createdAt: Number(item.createdAt) || now(),
      updatedAt: Number(item.updatedAt) || now()
    }));
}

function read(): AiConversation[] {
  if (typeof window === 'undefined') return [];
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return [];
  try {
    return normalizeConversations(JSON.parse(raw));
  } catch {
    return [];
  }
}

function write(conversations: AiConversation[]) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
}

function sortByUpdatedAt(conversations: AiConversation[]) {
  return [...conversations].sort((left, right) => right.updatedAt - left.updatedAt);
}

export const aiConversationService = {
  listConversations() {
    return sortByUpdatedAt(read());
  },

  listConversationsByProblem(problemId: string | number) {
    const id = String(problemId);
    return sortByUpdatedAt(read().filter((conversation) => String(conversation.problemId) === id));
  },

  getConversation(conversationId: string) {
    return read().find((conversation) => conversation.id === conversationId);
  },

  createConversation(payload: CreateAiConversationPayload) {
    const timestamp = now();
    const conversation: AiConversation = {
      id: createLocalId('conv'),
      remoteConversationId: payload.remoteConversationId,
      problemId: payload.problemId === undefined ? undefined : String(payload.problemId),
      problemTitle: payload.problemTitle,
      problemDifficulty: payload.problemDifficulty,
      problemTags: payload.problemTags ?? [],
      source: payload.source,
      title: payload.title || '新的辅导对话',
      mode: payload.mode,
      messages: [],
      createdAt: timestamp,
      updatedAt: timestamp
    };
    write([conversation, ...read()]);
    return conversation;
  },

  appendMessage(conversationId: string, message: Omit<AiMessage, 'id' | 'conversationId' | 'createdAt'> & Partial<Pick<AiMessage, 'id' | 'createdAt'>>) {
    const conversations = read();
    const timestamp = now();
    const index = conversations.findIndex((conversation) => conversation.id === conversationId);
    if (index < 0) return undefined;
    const nextMessage: AiMessage = {
      id: message.id || createLocalId('msg'),
      conversationId,
      problemId: message.problemId === undefined ? undefined : String(message.problemId),
      role: message.role,
      content: message.content,
      createdAt: message.createdAt || timestamp,
      status: message.status
    };
    conversations[index] = {
      ...conversations[index],
      messages: [...conversations[index].messages, nextMessage],
      updatedAt: timestamp
    };
    write(conversations);
    return nextMessage;
  },

  updateMessage(conversationId: string, messageId: string, patch: AiMessagePatch) {
    const conversations = read();
    const conversationIndex = conversations.findIndex((conversation) => conversation.id === conversationId);
    if (conversationIndex < 0) return undefined;
    const messages = conversations[conversationIndex].messages.map((message) =>
      message.id === messageId ? { ...message, ...patch } : message
    );
    const nextConversation = {
      ...conversations[conversationIndex],
      messages,
      updatedAt: now()
    };
    conversations[conversationIndex] = nextConversation;
    write(conversations);
    return messages.find((message) => message.id === messageId);
  },

  updateConversation(conversationId: string, patch: AiConversationPatch) {
    const conversations = read();
    const index = conversations.findIndex((conversation) => conversation.id === conversationId);
    if (index < 0) return undefined;
    const nextConversation = {
      ...conversations[index],
      ...patch,
      updatedAt: patch.updatedAt ?? now()
    };
    conversations[index] = nextConversation;
    write(conversations);
    return nextConversation;
  },

  deleteConversation(conversationId: string) {
    write(read().filter((conversation) => conversation.id !== conversationId));
  },

  clearConversationsByProblem(problemId: string | number) {
    const id = String(problemId);
    write(read().filter((conversation) => String(conversation.problemId) !== id));
  },

  getProblemFilters(): AiProblemFilter[] {
    const grouped = new Map<string, AiProblemFilter>();
    read().forEach((conversation) => {
      const key = conversation.problemId === undefined ? '__unlinked__' : String(conversation.problemId);
      const current = grouped.get(key);
      grouped.set(key, {
        problemId: conversation.problemId,
        problemTitle: conversation.problemTitle,
        count: (current?.count ?? 0) + 1
      });
    });
    return [...grouped.values()].sort((left, right) => right.count - left.count);
  }
};

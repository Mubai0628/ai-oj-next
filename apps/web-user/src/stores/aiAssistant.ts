import { defineStore } from 'pinia';
import { aiConversationService } from '@/services/aiConversationService';
import type {
  AiConversation,
  AiConversationPatch,
  AiMessage,
  AiMessagePatch,
  AiProblemFilter,
  CreateAiConversationPayload
} from '@/types/ai-assistant';

export const useAiAssistantStore = defineStore('aiAssistant', {
  state: () => ({
    conversations: [] as AiConversation[],
    loaded: false
  }),

  getters: {
    sortedConversations(state): AiConversation[] {
      return [...state.conversations].sort((left, right) => right.updatedAt - left.updatedAt);
    },
    problemFilters(state): AiProblemFilter[] {
      const grouped = new Map<string, AiProblemFilter>();
      state.conversations.forEach((conversation) => {
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
  },

  actions: {
    load() {
      this.conversations = aiConversationService.listConversations();
      this.loaded = true;
    },

    ensureLoaded() {
      if (!this.loaded) this.load();
    },

    listByProblem(problemId: string | number) {
      this.ensureLoaded();
      const id = String(problemId);
      return this.sortedConversations.filter((conversation) => String(conversation.problemId) === id);
    },

    getConversation(conversationId?: string) {
      this.ensureLoaded();
      return this.conversations.find((conversation) => conversation.id === conversationId);
    },

    createConversation(payload: CreateAiConversationPayload) {
      const conversation = aiConversationService.createConversation(payload);
      this.load();
      return conversation;
    },

    appendMessage(
      conversationId: string,
      message: Omit<AiMessage, 'id' | 'conversationId' | 'createdAt'> & Partial<Pick<AiMessage, 'id' | 'createdAt'>>
    ) {
      const nextMessage = aiConversationService.appendMessage(conversationId, message);
      this.load();
      return nextMessage;
    },

    updateMessage(conversationId: string, messageId: string, patch: AiMessagePatch) {
      const message = aiConversationService.updateMessage(conversationId, messageId, patch);
      this.load();
      return message;
    },

    updateConversation(conversationId: string, patch: AiConversationPatch) {
      const conversation = aiConversationService.updateConversation(conversationId, patch);
      this.load();
      return conversation;
    },

    deleteConversation(conversationId: string) {
      aiConversationService.deleteConversation(conversationId);
      this.load();
    },

    clearConversationsByProblem(problemId: string | number) {
      aiConversationService.clearConversationsByProblem(problemId);
      this.load();
    }
  }
});

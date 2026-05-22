import type { Difficulty, EntityId } from '@aioj/api-client';

export type AiMode = 'hint' | 'debug' | 'edge' | 'optimize';
export type AiConversationSource = 'problem_detail' | 'ai_tutor';
export type AiMessageRole = 'user' | 'assistant' | 'system';
export type AiMessageStatus = 'sending' | 'success' | 'error';

export interface AiMessage {
  id: string;
  conversationId: string;
  problemId?: EntityId;
  role: AiMessageRole;
  content: string;
  createdAt: number;
  status?: AiMessageStatus;
}

export interface AiConversation {
  id: string;
  remoteConversationId?: string;
  problemId?: EntityId;
  problemTitle?: string;
  problemDifficulty?: Difficulty | string;
  problemTags?: string[];
  source: AiConversationSource;
  title: string;
  mode: AiMode;
  messages: AiMessage[];
  createdAt: number;
  updatedAt: number;
}

export interface AiProblemFilter {
  problemId?: EntityId;
  problemTitle?: string;
  count: number;
}

export interface CreateAiConversationPayload {
  problemId?: EntityId;
  problemTitle?: string;
  problemDifficulty?: Difficulty | string;
  problemTags?: string[];
  source: AiConversationSource;
  title?: string;
  mode: AiMode;
  remoteConversationId?: string;
}

export type AiConversationPatch = Partial<
  Pick<
    AiConversation,
    'remoteConversationId' | 'problemTitle' | 'problemDifficulty' | 'problemTags' | 'title' | 'mode' | 'updatedAt'
  >
>;

export type AiMessagePatch = Partial<Pick<AiMessage, 'content' | 'status'>>;

export type Role = 'STUDENT' | 'TEACHER' | 'ADMIN';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'CHALLENGE';
export type EntityId = string;
export type SubmissionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'COMPILE_ERROR'
  | 'RUNTIME_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'SYSTEM_ERROR';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  details?: Record<string, string> | null;
  traceId: string;
  timestamp: string;
}

export type ApiErrorDetails = Record<string, string>;

export class ApiError extends Error {
  readonly code: number;
  readonly details: ApiErrorDetails | null;
  readonly traceId: string | null;
  readonly serverMessage: string;

  constructor(code: number, serverMessage: string, details: ApiErrorDetails | null = null, traceId: string | null = null) {
    super(serverMessage);
    this.name = 'ApiError';
    this.code = code;
    this.details = details;
    this.traceId = traceId;
    this.serverMessage = serverMessage;
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get userMessage(): string {
    const resolved = messageResolver?.(this.code, this.serverMessage);
    return resolved || this.serverMessage || 'Unknown error';
  }

  fieldError(path: string): string | undefined {
    return this.details?.[path];
  }
}

let messageResolver: ((code: number, fallback: string) => string | undefined) | null = null;

export function setApiErrorMessageResolver(resolver: (code: number, fallback: string) => string | undefined): void {
  messageResolver = resolver;
}

export interface PageResponse<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresAt: string;
  userId: EntityId;
  account: string;
  displayName: string;
  roles: Role[];
}

export interface UserProfileResponse {
  userId: EntityId;
  account: string;
  displayName: string;
  email?: string;
  roles: Role[];
}

export interface AdminUserResponse extends UserProfileResponse {
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RoleResponse {
  role: Role;
  label: string;
}

export interface TestCaseDto {
  input: string;
  expectedOutput: string;
  sample: boolean;
}

export type TestcasePackageStatus = 'UPLOADING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface TestcasePackageCase {
  id: EntityId;
  name: string;
  inputPath: string;
  outputPath: string;
  sample: boolean;
  score: number;
  inputSizeBytes: number;
  outputSizeBytes: number;
  sortOrder: number;
}

export interface TestcasePackageResponse {
  id: EntityId;
  problemId: EntityId;
  version: string;
  fileName: string;
  fileSizeBytes: number;
  sha256: string;
  status: TestcasePackageStatus;
  active: boolean;
  caseCount: number;
  sampleCount: number;
  storageProvider: string;
  createdAt: string;
  activatedAt?: string | null;
  errorMessage?: string | null;
  cases?: TestcasePackageCase[];
}

export interface TestcaseUploadInitResponse {
  uploadId: string;
  status: TestcasePackageStatus;
  packageId?: EntityId | null;
  uploadedChunks: number[];
  chunkSizeBytes: number;
  totalChunks: number;
  expiresAt: string;
  message?: string | null;
}

export interface TestcaseUploadStatusResponse {
  uploadId: string;
  status: TestcasePackageStatus;
  uploadedChunks: number[];
  totalChunks: number;
  progress: number;
  packageId?: EntityId | null;
  errorMessage?: string | null;
}

export interface ProblemPayload {
  title: string;
  difficulty: Difficulty;
  statement: string;
  notes?: string;
  tags: string[];
  testCases: TestCaseDto[];
  timeLimitMillis: number;
  memoryLimitKb: number;
}

export interface ProblemResponse {
  id: EntityId;
  title: string;
  difficulty: Difficulty;
  statement: string;
  notes?: string | null;
  tags: string[];
  samples: TestCaseDto[];
  timeLimitMillis: number;
  memoryLimitKb: number;
  aiGenerated: boolean;
  createdAt: string;
}

export interface SubmissionResponse {
  id: EntityId;
  problemId: EntityId;
  userId: EntityId;
  language: string;
  status: SubmissionStatus;
  judgeMessage: string;
  timeMillis?: number;
  memoryKb?: number;
  createdAt: string;
  judgedAt?: string;
}

export interface AiChatMessageResponse {
  conversationId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  model?: string;
  createdAt: string;
}

export interface AiConversationResponse {
  conversationId: string;
  problemId?: EntityId;
  title?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AiUsageResponse {
  usedToday: number;
  dailyLimit: number;
  usedThisMonth: number;
  monthlyLimit: number;
}

export interface ProblemDraftResponse {
  id: EntityId;
  title: string;
  difficulty: Difficulty | string;
  statement: string;
  tags: string[];
  validationStatus: string;
  validationErrors: string[];
  testCases: TestCaseDto[];
  timeLimitMillis: number;
  memoryLimitKb: number;
  importedProblemId?: EntityId | null;
  model: string;
  promptTokens: number;
  completionTokens: number;
  createdAt: string;
  refinedFromDraftId?: EntityId | null;
  refineNote?: string | null;
}

export interface ProblemListParams {
  page?: number;
  pageSize?: number;
  keyword?: string;
  difficulty?: Difficulty | '';
  tag?: string;
}

export interface SubmissionListParams {
  page?: number;
  pageSize?: number;
  problemId?: EntityId;
  userId?: EntityId;
  status?: SubmissionStatus | '';
  mine?: boolean;
}

export type AuthExpiredReason = 'unauthorized' | 'refresh_failed';

const TOKEN_KEY = 'aioj.accessToken';
const REFRESH_KEY = 'aioj.refreshToken';
const USER_KEY = 'aioj.user';

export const apiBaseUrl = () => import.meta.env.VITE_API_BASE_URL || 'http://localhost:8101';

export const authStore = {
  get accessToken() {
    return localStorage.getItem(TOKEN_KEY);
  },
  get refreshToken() {
    return localStorage.getItem(REFRESH_KEY);
  },
  get user(): TokenResponse | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) as TokenResponse : null;
  },
  save(tokens: TokenResponse) {
    localStorage.setItem(TOKEN_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(tokens));
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  }
};

function notifyAuthExpired(reason: AuthExpiredReason) {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent('aioj:auth-expired', { detail: { reason } }));
}

function expireAuth(reason: AuthExpiredReason) {
  const hadSession = Boolean(authStore.accessToken || authStore.refreshToken);
  authStore.clear();
  if (hadSession) notifyAuthExpired(reason);
}

function queryString(params: Record<string, unknown> = {}) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}

function preserveLargeIntegerIds(text: string) {
  return text.replace(/("(?:(?:[A-Za-z0-9_]*Id)|id)"\s*:\s*)(-?\d{16,})/g, '$1"$2"');
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text();
  const payload = text ? JSON.parse(preserveLargeIntegerIds(text)) as ApiResponse<T> : null;
  if (!response.ok || !payload || payload.code !== 0) {
    const code = payload?.code ?? response.status * 100;
    const message = payload?.message || `Request failed: ${response.status}`;
    const details = payload?.details && typeof payload.details === 'object' ? payload.details as ApiErrorDetails : null;
    const traceId = payload?.traceId ?? null;
    throw new ApiError(code, message, details, traceId);
  }
  return payload.data;
}

async function refreshAccessToken() {
  if (!authStore.refreshToken) {
    throw new Error('Login expired');
  }
  const response = await fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${authStore.refreshToken}`,
      'Content-Type': 'application/json'
    }
  });
  const tokens = await parseResponse<TokenResponse>(response);
  authStore.save(tokens);
  return tokens;
}

async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (authStore.accessToken) {
    headers.set('Authorization', `Bearer ${authStore.accessToken}`);
  }
  const response = await fetch(`${apiBaseUrl()}${path}`, { ...init, headers });
  if (response.status === 401 && retry && authStore.refreshToken) {
    try {
      await refreshAccessToken();
      return request<T>(path, init, false);
    } catch (error) {
      expireAuth('refresh_failed');
      throw error;
    }
  }
  if (response.status === 401) {
    expireAuth('unauthorized');
  }
  return parseResponse<T>(response);
}

export const api = {
  login: (account: string, password: string) =>
    request<TokenResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ account, password })
    }),
  register: (payload: { account: string; password: string; displayName: string; email?: string; role?: 'STUDENT' | 'TEACHER' }) =>
    request<TokenResponse>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(payload) }),
  refresh: () => refreshAccessToken(),
  logout: () => request<boolean>('/api/v1/auth/logout', { method: 'POST', body: '{}' }).finally(() => authStore.clear()),
  me: () => request<UserProfileResponse>('/api/v1/users/me'),
  updateMe: (payload: { displayName: string; email?: string }) =>
    request<UserProfileResponse>('/api/v1/users/me', { method: 'PUT', body: JSON.stringify(payload) }),
  changePassword: (payload: { currentPassword: string; newPassword: string }) =>
    request<boolean>('/api/v1/users/me/password', { method: 'PUT', body: JSON.stringify(payload) }),

  roles: () => request<RoleResponse[]>('/api/v1/admin/roles'),
  users: (params: { page?: number; pageSize?: number; keyword?: string; search?: string; role?: Role | ''; enabled?: boolean | '' } = {}) =>
    request<PageResponse<AdminUserResponse>>(`/api/v1/admin/users${queryString({
      page: 1,
      pageSize: 20,
      ...params,
      search: params.search || params.keyword,
      keyword: undefined
    })}`),
  createUser: (payload: { account: string; password: string; displayName: string; email?: string; roles: Role[]; enabled?: boolean }) =>
    request<AdminUserResponse>('/api/v1/admin/users', { method: 'POST', body: JSON.stringify(payload) }),
  updateUser: (id: EntityId, payload: { displayName: string; email?: string; roles: Role[]; enabled?: boolean }) =>
    request<AdminUserResponse>(`/api/v1/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteUser: (id: EntityId) => request<boolean>(`/api/v1/admin/users/${id}`, { method: 'DELETE' }),

  problems: (params: ProblemListParams = {}) =>
    request<PageResponse<ProblemResponse>>(`/api/v1/problems${queryString({ page: 1, pageSize: 20, ...params })}`),
  problem: (id: EntityId) => request<ProblemResponse>(`/api/v1/problems/${id}`),
  createProblem: (payload: ProblemPayload) =>
    request<ProblemResponse>('/api/v1/problems', { method: 'POST', body: JSON.stringify(payload) }),
  updateProblem: (id: EntityId, payload: ProblemPayload) =>
    request<ProblemResponse>(`/api/v1/problems/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deleteProblem: (id: EntityId) => request<void>(`/api/v1/problems/${id}`, { method: 'DELETE' }),
  initTestcasePackage: (
    problemId: EntityId,
    payload: { fileName: string; fileSizeBytes: number; sha256: string; chunkSizeBytes: number; totalChunks: number }
  ) =>
    request<TestcaseUploadInitResponse>(`/api/v1/problems/${problemId}/testcase-packages/init`, {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  uploadTestcaseChunk: (problemId: EntityId, uploadId: string, index: number, chunk: Blob, chunkSha256?: string) =>
    request<TestcaseUploadStatusResponse>(
      `/api/v1/problems/${problemId}/testcase-packages/uploads/${encodeURIComponent(uploadId)}/chunks/${index}`,
      {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/octet-stream',
          ...(chunkSha256 ? { 'X-Chunk-Sha256': chunkSha256 } : {})
        },
        body: chunk
      }
    ),
  completeTestcaseUpload: (problemId: EntityId, uploadId: string) =>
    request<TestcasePackageResponse>(
      `/api/v1/problems/${problemId}/testcase-packages/uploads/${encodeURIComponent(uploadId)}/complete`,
      { method: 'POST', body: '{}' }
    ),
  testcaseUploadStatus: (problemId: EntityId, uploadId: string) =>
    request<TestcaseUploadStatusResponse>(
      `/api/v1/problems/${problemId}/testcase-packages/uploads/${encodeURIComponent(uploadId)}/status`
    ),
  testcasePackages: (problemId: EntityId) =>
    request<TestcasePackageResponse[]>(`/api/v1/problems/${problemId}/testcase-packages`),
  activateTestcasePackage: (problemId: EntityId, packageId: EntityId) =>
    request<TestcasePackageResponse>(`/api/v1/problems/${problemId}/testcase-packages/${packageId}/activate`, {
      method: 'POST',
      body: '{}'
    }),

  submit: (payload: { problemId: EntityId; language: string; code: string }) =>
    request<SubmissionResponse>('/api/v1/submissions', { method: 'POST', body: JSON.stringify(payload) }),
  submission: (id: EntityId) => request<SubmissionResponse>(`/api/v1/submissions/${id}`),
  submissions: (params: SubmissionListParams = {}) =>
    request<PageResponse<SubmissionResponse>>(`/api/v1/submissions${queryString({ page: 1, pageSize: 20, ...params })}`),
  mySubmissions: (params: SubmissionListParams = {}) =>
    request<PageResponse<SubmissionResponse>>(`/api/v1/submissions${queryString({ page: 1, pageSize: 20, mine: true, ...params })}`),

  aiSend: (payload: { conversationId?: string; problemId?: EntityId; message: string }) =>
    request<AiChatMessageResponse>('/api/v1/ai/chat/send', {
      method: 'POST',
      body: JSON.stringify(payload)
    }),
  aiConversations: (params: { page?: number; pageSize?: number } = {}) =>
    request<PageResponse<AiConversationResponse>>(`/api/v1/ai/conversations${queryString({ page: 1, pageSize: 20, ...params })}`),
  aiHistory: (conversationId: string) =>
    request<AiChatMessageResponse[]>(`/api/v1/ai/conversations/${encodeURIComponent(conversationId)}/messages`),
  deleteAiConversation: (conversationId: string) =>
    request<void>(`/api/v1/ai/conversations/${encodeURIComponent(conversationId)}`, { method: 'DELETE' }),
  generateDraft: (payload: { topic: string; difficulty?: string; teachingGoal?: string }) =>
    request<ProblemDraftResponse>('/api/v1/ai/problem-drafts/generate', { method: 'POST', body: JSON.stringify(payload) }),
  problemDrafts: (params: { page?: number; pageSize?: number; status?: string } = {}) =>
    request<PageResponse<ProblemDraftResponse>>(`/api/v1/admin/problem-drafts${queryString({ page: 1, pageSize: 20, ...params })}`),
  approveDraft: (id: EntityId, importProblem = false) =>
    request<ProblemDraftResponse>(`/api/v1/admin/problem-drafts/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ importProblem })
    }),
  rejectDraft: (id: EntityId, reasonNote?: string) =>
    request<ProblemDraftResponse>(`/api/v1/admin/problem-drafts/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reasonNote })
    }),
  deleteDraft: (id: EntityId) =>
    request<void>(`/api/v1/admin/problem-drafts/${id}`, { method: 'DELETE' }),
  usage: () => request<AiUsageResponse>('/api/v1/ai/usage/me')
};

export async function streamAi(
  payload: { conversationId?: string; problemId?: EntityId; message: string },
  onEvent: (event: 'meta' | 'message' | 'error' | 'done' | string, data: string) => void
) {
  const response = await fetch(`${apiBaseUrl()}/api/v1/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(authStore.accessToken ? { Authorization: `Bearer ${authStore.accessToken}` } : {})
    },
    body: JSON.stringify(payload)
  });
  if (response.status === 401 && authStore.refreshToken) {
    try {
      await refreshAccessToken();
      return streamAi(payload, onEvent);
    } catch (error) {
      expireAuth('refresh_failed');
      throw error;
    }
  }
  if (response.status === 401) {
    expireAuth('unauthorized');
  }
  if (!response.ok) {
    throw new Error(`AI stream failed: ${response.status}`);
  }
  if (!response.body) {
    throw new Error('Streaming is not supported by this browser');
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let sawEvent = false;
  let sawDone = false;

  const dispatchBlock = (block: string) => {
    if (!block.trim()) return;
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const rawLine of block.split('\n')) {
      if (!rawLine || rawLine.startsWith(':')) continue;
      const colon = rawLine.indexOf(':');
      const field = colon >= 0 ? rawLine.slice(0, colon) : rawLine;
      let value = colon >= 0 ? rawLine.slice(colon + 1) : '';
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') eventName = value || 'message';
      if (field === 'data') dataLines.push(value);
    }
    const data = dataLines.join('\n');
    sawEvent = true;
    if (eventName === 'done') sawDone = true;
    onEvent(eventName, data);
  };

  const feed = (text: string) => {
    buffer += text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      dispatchBlock(buffer.slice(0, boundary));
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf('\n\n');
    }
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      feed(decoder.decode(value, { stream: true }));
    }
    feed(decoder.decode());
    if (buffer.trim()) {
      dispatchBlock(buffer);
      buffer = '';
    }
  } catch (error) {
    if (sawDone || sawEvent) {
      if (!sawDone) onEvent('done', '[DONE]');
      return;
    }
    throw error;
  }
}

export { installErrorReporter, reportApiError } from './reporting';

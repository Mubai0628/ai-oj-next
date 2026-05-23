import type { Difficulty, EntityId, ProblemResponse, SubmissionResponse } from '@aioj/api-client';

export interface ProblemSampleModel {
  input: string;
  output: string;
  explanation?: string;
}

export interface ProblemDetailModel {
  id: EntityId;
  title: string;
  difficulty: Difficulty;
  statement: string;
  notes?: string | null;
  tags: string[];
  samples: ProblemSampleModel[];
  timeLimitMillis: number;
  memoryLimitMb: number;
  aiGenerated: boolean;
  createdAt: string;
}

export interface CodeLanguage {
  label: string;
  value: string;
  template: string;
}

export interface EditorCursorState {
  line: number;
  column: number;
}

export interface SubmitResultView {
  id?: EntityId;
  status?: string;
  language?: string;
  timeMillis?: number;
  memoryKb?: number;
  createdAt?: string;
}

export type ProblemTabKey = 'statement' | 'samples' | 'notes' | 'related';
export type SplitPaneRatio = number;

export interface AiDrawerState {
  open: boolean;
}

export function adaptProblem(problem: ProblemResponse): ProblemDetailModel {
  return {
    id: problem.id,
    title: problem.title,
    difficulty: problem.difficulty,
    statement: problem.statement,
    notes: problem.notes ?? null,
    tags: problem.tags ?? [],
    samples: (problem.samples ?? []).map((sample) => ({
      input: sample.input,
      output: sample.expectedOutput
    })),
    timeLimitMillis: problem.timeLimitMillis,
    memoryLimitMb: Math.round(problem.memoryLimitKb / 1024),
    aiGenerated: problem.aiGenerated,
    createdAt: problem.createdAt
  };
}

export function adaptSubmitResult(result: SubmissionResponse): SubmitResultView {
  return {
    id: result.id,
    status: result.status,
    language: result.language,
    timeMillis: result.timeMillis,
    memoryKb: result.memoryKb,
    createdAt: result.createdAt
  };
}

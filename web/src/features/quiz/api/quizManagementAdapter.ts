import {
  createReviewSession,
  getLatestReview,
  getPendingSelfAssessment,
  getQuizSets,
  getReviewCandidates,
  renameQuizSet,
} from './quiz.api'
import type {
  GetQuizSetsParams,
  LatestReview,
  PendingSelfAssessment,
  ReviewCandidate,
  ReviewCandidateList,
  ReviewSession,
  QuizSetPage,
  QuizSetSummary,
  RenameQuizSetResponse,
} from './quiz.types'
import { quizRuntimeMode, type QuizRuntimeMode } from '../model/quizFeature'

export type QuizManagementMode = QuizRuntimeMode

export const quizManagementMode: QuizManagementMode = quizRuntimeMode

const mockQuizSets: QuizSetSummary[] = [
  {
    quizSetId: 'quiz-set-network',
    quizTitle: 'TCP/IP 기본 퀴즈',
    materialId: 'material-network',
    materialTitle: '네트워크 기초',
    status: 'READY',
    questionCount: 10,
    createdAt: '2026-08-25T01:00:00Z',
    updatedAt: '2026-08-28T02:00:00Z',
    lastAttemptAt: '2026-08-28T02:00:00Z',
  },
  {
    quizSetId: 'quiz-set-os-essay',
    quizTitle: '운영체제 서술형 점검',
    materialId: 'material-operating-system',
    materialTitle: '운영체제 정리: 프로세스와 스레드, 스케줄링의 차이',
    status: 'READY',
    questionCount: 8,
    createdAt: '2026-08-24T01:00:00Z',
    updatedAt: '2026-08-27T01:00:00Z',
    lastAttemptAt: '2026-08-27T01:00:00Z',
  },
  {
    quizSetId: 'quiz-set-data-structure',
    quizTitle: '자료구조 핵심 개념 퀴즈',
    materialId: 'material-data-structure',
    materialTitle: '자료구조 핵심 개념',
    status: 'READY',
    questionCount: 10,
    createdAt: '2026-08-23T01:00:00Z',
    updatedAt: '2026-08-26T01:00:00Z',
    lastAttemptAt: null,
  },
  {
    quizSetId: 'quiz-set-generating',
    quizTitle: '네트워크 심화 퀴즈',
    materialId: 'material-network',
    materialTitle: '네트워크 기초',
    status: 'GENERATING',
    questionCount: null,
    createdAt: '2026-08-28T03:00:00Z',
    updatedAt: '2026-08-28T03:00:00Z',
    lastAttemptAt: null,
  },
]

const mockPendingByQuizSet = new Map<string, PendingSelfAssessment>([
  [
    'quiz-set-os-essay',
    {
      attemptId: '550e8400-e29b-41d4-a716-446655440001',
      quizSetId: 'quiz-set-os-essay',
      status: 'SELF_ASSESSMENT_REQUIRED',
      pendingEssayQuestionIds: ['question-4'],
    },
  ],
])

let mockLatestReview: LatestReview = {
  sourceAttemptId: '550e8400-e29b-41d4-a716-446655440000',
  quizSetId: 'quiz-set-network',
  attemptNumber: 2,
  quizTitle: 'TCP/IP 기본 퀴즈',
  materialTitle: '네트워크 기초',
  completedAt: '2026-08-28T02:00:00Z',
  totalQuestionCount: 10,
  reviewQuestionCount: 3,
  activeReviewSessionId: 'review-session-network',
}

const mockReviewCandidates: ReviewCandidate[] = [
  {
    quizSetId: 'quiz-set-os-essay',
    quizTitle: '운영체제 서술형 점검',
    materialTitle: '운영체제 정리: 프로세스와 스레드, 스케줄링의 차이',
    sourceAttemptId: '550e8400-e29b-41d4-a716-446655440001',
    pendingSelfAssessmentAttemptId: '550e8400-e29b-41d4-a716-446655440001',
    activeReviewSessionId: null,
    reviewQuestionCount: 2,
    lastLearningActivityAt: '2026-08-27T01:00:00Z',
  },
  {
    quizSetId: 'quiz-set-data-structure',
    quizTitle: '자료구조 핵심 개념 퀴즈',
    materialTitle: '자료구조 핵심 개념',
    sourceAttemptId: '550e8400-e29b-41d4-a716-446655440002',
    pendingSelfAssessmentAttemptId: null,
    activeReviewSessionId: null,
    reviewQuestionCount: 2,
    lastLearningActivityAt: '2026-08-26T01:00:00Z',
  },
]

function ensureAvailable() {
  if (quizManagementMode === 'disabled') {
    throw new Error('퀴즈 관리 API가 아직 배포되지 않았어요.')
  }
}

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException('요청이 취소되었습니다.', 'AbortError')
}

export async function listManagedQuizSets(
  params: GetQuizSetsParams,
  signal?: AbortSignal,
): Promise<QuizSetPage> {
  ensureAvailable()
  if (quizManagementMode === 'api') return getQuizSets(params, signal)
  throwIfAborted(signal)
  const query = params.query?.trim().toLocaleLowerCase('ko-KR') ?? ''
  const size = params.size ?? 6
  const filtered = mockQuizSets.filter((item) =>
    item.quizTitle.toLocaleLowerCase('ko-KR').includes(query),
  )
  const focusedIndex = params.focusQuizSetId
    ? filtered.findIndex((item) => item.quizSetId === params.focusQuizSetId && item.status === 'READY')
    : -1
  if (params.focusQuizSetId && focusedIndex < 0) throw new Error('퀴즈를 찾지 못했어요.')
  const resolvedPage = focusedIndex >= 0 ? Math.floor(focusedIndex / size) + 1 : params.page
  const start = (resolvedPage - 1) * size
  return {
    items: filtered.slice(start, start + size).map((item) => ({ ...item })),
    page: resolvedPage,
    size,
    totalElements: filtered.length,
    totalPages: Math.ceil(filtered.length / size),
  }
}

export async function getManagedPendingSelfAssessment(
  quizSetId: string,
  signal?: AbortSignal,
) {
  ensureAvailable()
  if (quizManagementMode === 'api') return getPendingSelfAssessment(quizSetId, signal)
  throwIfAborted(signal)
  return mockPendingByQuizSet.get(quizSetId) ?? null
}

export async function getManagedLatestReview(signal?: AbortSignal) {
  ensureAvailable()
  if (quizManagementMode === 'api') return getLatestReview(signal)
  throwIfAborted(signal)
  return { ...mockLatestReview }
}

export async function getManagedReviewCandidates(
  limit: number,
  signal?: AbortSignal,
): Promise<ReviewCandidateList> {
  ensureAvailable()
  if (quizManagementMode === 'api') return getReviewCandidates(limit, signal)
  throwIfAborted(signal)
  return { items: mockReviewCandidates.slice(0, limit).map((item) => ({ ...item })) }
}

export async function startManagedReviewSession(sourceAttemptId: string): Promise<ReviewSession> {
  ensureAvailable()
  if (quizManagementMode === 'api') return createReviewSession(sourceAttemptId)
  const candidate = mockReviewCandidates.find((item) => item.sourceAttemptId === sourceAttemptId)
  if (!candidate) throw new Error('복습할 퀴즈를 찾지 못했어요.')
  return {
    reviewSessionId: candidate.activeReviewSessionId ?? `review-${candidate.quizSetId}`,
    sourceAttemptId,
    status: 'SOLVING',
    reviewQuestionCount: candidate.reviewQuestionCount,
    pendingEssayQuestionIds: [],
  }
}

export async function renameManagedQuizSet(
  quizSetId: string,
  quizTitle: string,
): Promise<RenameQuizSetResponse> {
  ensureAvailable()
  if (quizManagementMode === 'api') return renameQuizSet(quizSetId, quizTitle)
  const item = mockQuizSets.find((candidate) => candidate.quizSetId === quizSetId)
  if (!item) throw new Error('퀴즈를 찾지 못했어요.')
  const updatedAt = new Date().toISOString()
  item.quizTitle = quizTitle
  item.updatedAt = updatedAt
  if (mockLatestReview.quizSetId === quizSetId) {
    mockLatestReview = { ...mockLatestReview, quizTitle }
  }
  return { quizSetId, quizTitle, updatedAt }
}

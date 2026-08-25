import type { ApiResponse } from '@/features/auth/api/auth.types'
import { protectedApi } from '@/shared/api/protectedApi'
import { unwrapApiResponse } from '@/shared/api/apiError'

import type {
  ActiveQuizSet,
  CreateQuizSetRequest,
  CreateQuizSetResponse,
  EssayAssessmentResponse,
  LatestReview,
  PendingSelfAssessment,
  QuizResultResponse,
  QuizSetState,
  QuizSubmissionPayload,
  QuizSubmissionResponse,
  ReviewEssayAssessmentResponse,
  ReviewResultResponse,
  ReviewSession,
  ReviewSessionEnvelope,
  ReviewSubmissionResponse,
} from './quiz.types'
import type { QuizBinaryOutcome, QuizResultOutcome } from '@/pages/quiz/quiz.types'

async function getData<T>(url: string, signal?: AbortSignal) {
  const response = await protectedApi.get<ApiResponse<T>>(url, { signal })
  return unwrapApiResponse(response.data)
}

export async function createQuizSet(
  materialId: string,
  payload: CreateQuizSetRequest,
  idempotencyKey: string,
) {
  const response = await protectedApi.post<ApiResponse<CreateQuizSetResponse>>(
    `/api/v1/learning-materials/${materialId}/quiz-sets`,
    payload,
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  return unwrapApiResponse(response.data)
}

export const getActiveQuizSet = (materialId: string, signal?: AbortSignal) =>
  getData<ActiveQuizSet | null>(`/api/v1/learning-materials/${materialId}/quiz-sets/active`, signal)
export const getQuizSet = (quizSetId: string, signal?: AbortSignal) =>
  getData<QuizSetState>(`/api/v1/quiz-sets/${quizSetId}`, signal)

export async function submitQuiz(
  quizSetId: string,
  attemptId: string,
  payload: QuizSubmissionPayload,
) {
  const response = await protectedApi.put<ApiResponse<QuizSubmissionResponse>>(
    `/api/v1/quiz-sets/${quizSetId}/attempts/${attemptId}`,
    payload,
  )
  return unwrapApiResponse(response.data)
}

export const getPendingSelfAssessment = (quizSetId: string, signal?: AbortSignal) =>
  getData<PendingSelfAssessment | null>(
    `/api/v1/quiz-sets/${quizSetId}/attempts/pending-self-assessment`,
    signal,
  )
export const getQuizResult = (attemptId: string, signal?: AbortSignal) =>
  getData<QuizResultResponse>(`/api/v1/quiz-attempts/${attemptId}/result`, signal)

export async function saveEssayAssessment(
  attemptId: string,
  questionId: string,
  assessment: QuizResultOutcome,
) {
  const response = await protectedApi.put<ApiResponse<EssayAssessmentResponse>>(
    `/api/v1/quiz-attempts/${attemptId}/essay-assessments/${questionId}`,
    { assessment },
  )
  return unwrapApiResponse(response.data)
}

export async function updateShortAnswerGrading(
  attemptId: string,
  questionId: string,
  outcome: QuizBinaryOutcome,
) {
  const response = await protectedApi.put<ApiResponse<QuizResultResponse>>(
    `/api/v1/quiz-attempts/${attemptId}/short-answer-gradings/${questionId}`,
    { outcome },
  )
  return unwrapApiResponse(response.data)
}

export const getLatestReview = (signal?: AbortSignal) =>
  getData<LatestReview>('/api/v1/quiz-reviews/latest', signal)

export async function createReviewSession(sourceAttemptId: string) {
  const response = await protectedApi.post<ApiResponse<ReviewSessionEnvelope>>(
    '/api/v1/review-sessions',
    { sourceAttemptId },
  )
  return unwrapApiResponse(response.data).reviewSession
}
export const getReviewSession = (reviewSessionId: string, signal?: AbortSignal) =>
  getData<ReviewSession>(`/api/v1/review-sessions/${reviewSessionId}`, signal)
export async function submitReview(
  reviewSessionId: string,
  payload: QuizSubmissionPayload,
) {
  const response = await protectedApi.put<ApiResponse<ReviewSubmissionResponse>>(
    `/api/v1/review-sessions/${reviewSessionId}/submission`,
    payload,
  )
  return unwrapApiResponse(response.data)
}
export const getReviewResult = (reviewSessionId: string, signal?: AbortSignal) =>
  getData<ReviewResultResponse>(`/api/v1/review-sessions/${reviewSessionId}/result`, signal)
export async function saveReviewEssayAssessment(
  reviewSessionId: string,
  questionId: string,
  assessment: QuizResultOutcome,
) {
  const response = await protectedApi.put<ApiResponse<ReviewEssayAssessmentResponse>>(
    `/api/v1/review-sessions/${reviewSessionId}/essay-assessments/${questionId}`,
    { assessment },
  )
  return unwrapApiResponse(response.data)
}

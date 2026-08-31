import { queryOptions } from '@tanstack/react-query'

import { getLatestReview } from '../api/quiz.api'

export const QUIZ_SUMMARY_STALE_TIME = 5 * 60 * 1_000

export const quizQueryKeys = {
  reviews: ['private', 'quiz-review'] as const,
  active: (materialId: string) => ['private', 'quiz', 'active', materialId] as const,
  quizSet: (quizSetId: string) => ['private', 'quiz-set', quizSetId] as const,
  pendingSelfAssessment: (quizSetId: string) =>
    [...quizQueryKeys.quizSet(quizSetId), 'pending-self-assessment'] as const,
  attemptResult: (attemptId: string | undefined) =>
    ['private', 'quiz-attempt', attemptId, 'result'] as const,
  latestReview: ['private', 'quiz-review', 'latest'] as const,
  reviewCandidates: (limit: number) => [...quizQueryKeys.reviews, 'candidates', limit] as const,
  reviewSession: (reviewSessionId: string) =>
    ['private', 'review-session', reviewSessionId] as const,
  reviewResult: (reviewSessionId: string) =>
    [...quizQueryKeys.reviewSession(reviewSessionId), 'result'] as const,
}

export const latestReviewQueryOptions = () =>
  queryOptions({
    queryKey: quizQueryKeys.latestReview,
    queryFn: ({ signal }) => getLatestReview(signal),
    staleTime: QUIZ_SUMMARY_STALE_TIME,
  })

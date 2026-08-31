import { queryOptions, type QueryClient } from '@tanstack/react-query'

import {
  getManagedLatestReview,
  getManagedPendingSelfAssessment,
  getManagedReviewCandidates,
  listManagedQuizSets,
} from '../api/quizManagementAdapter'
import type { GetQuizSetsParams } from '../api/quiz.types'
import { QUIZ_SUMMARY_STALE_TIME, quizQueryKeys } from './quizQueries'

export const QUIZ_SET_PAGE_SIZE = 6

export const quizManagementKeys = {
  all: ['private', 'quiz-sets'] as const,
  list: (params: GetQuizSetsParams) => [...quizManagementKeys.all, 'list', params] as const,
}

export const quizManagementQueryOptions = {
  list: (params: GetQuizSetsParams) =>
    queryOptions({
      queryKey: quizManagementKeys.list(params),
      queryFn: ({ signal }) => listManagedQuizSets(params, signal),
      staleTime: QUIZ_SUMMARY_STALE_TIME,
    }),
  pendingSelfAssessment: (quizSetId: string) =>
    queryOptions({
      queryKey: quizQueryKeys.pendingSelfAssessment(quizSetId),
      queryFn: ({ signal }) => getManagedPendingSelfAssessment(quizSetId, signal),
      staleTime: QUIZ_SUMMARY_STALE_TIME,
    }),
  latestReview: () =>
    queryOptions({
      queryKey: quizQueryKeys.latestReview,
      queryFn: ({ signal }) => getManagedLatestReview(signal),
      staleTime: QUIZ_SUMMARY_STALE_TIME,
    }),
  reviewCandidates: (limit = 3) =>
    queryOptions({
      queryKey: quizQueryKeys.reviewCandidates(limit),
      queryFn: ({ signal }) => getManagedReviewCandidates(limit, signal),
      staleTime: QUIZ_SUMMARY_STALE_TIME,
    }),
}

export async function invalidateQuizManagementQueries(queryClient: QueryClient, quizSetId: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: quizManagementKeys.all }),
    queryClient.invalidateQueries({ queryKey: quizQueryKeys.quizSet(quizSetId) }),
    queryClient.invalidateQueries({ queryKey: quizQueryKeys.reviews }),
    queryClient.invalidateQueries({ queryKey: ['private', 'home'] }),
  ])
}

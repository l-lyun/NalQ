import type {
  LatestReview,
  PendingSelfAssessment,
  ReviewCandidate,
} from '../api/quiz.types'

export type LearningReviewAction =
  | { kind: 'navigate'; label: string; path: string }
  | { kind: 'start-review'; label: string; sourceAttemptId: string }

export function getReviewCandidatesEmptyMessage(
  recent: Pick<LatestReview, 'reviewQuestionCount' | 'activeReviewSessionId'> | null | undefined,
) {
  if (!recent) return '복습할 퀴즈가 없어요.'
  return recent.reviewQuestionCount > 0 || recent.activeReviewSessionId
    ? '추가로 복습할 퀴즈가 없어요.'
    : '최근 틀린 문제가 없어요! 훌륭합니다 🎉'
}

export function resolveRecentQuizAction(
  review: LatestReview,
  pending: PendingSelfAssessment | null,
): LearningReviewAction | null {
  if (!review.quizSetId || !review.sourceAttemptId) return null
  if (pending) {
    return {
      kind: 'navigate',
      label: '채점이 남았어요',
      path: `/quiz-sets/${review.quizSetId}`,
    }
  }
  if (review.activeReviewSessionId) {
    return {
      kind: 'navigate',
      label: '틀린 문제 복습하기',
      path: `/review-sessions/${review.activeReviewSessionId}`,
    }
  }
  if (review.reviewQuestionCount > 0) {
    return {
      kind: 'start-review',
      label: '틀린 문제 복습하기',
      sourceAttemptId: review.sourceAttemptId,
    }
  }
  return {
    kind: 'navigate',
    label: '결과 보기',
    path: `/quiz-attempts/${review.sourceAttemptId}/result`,
  }
}

export function resolveReviewCandidateAction(
  candidate: ReviewCandidate,
): LearningReviewAction {
  if (candidate.pendingSelfAssessmentAttemptId) {
    return {
      kind: 'navigate',
      label: '채점이 남았어요',
      path: `/quiz-sets/${candidate.quizSetId}`,
    }
  }
  if (candidate.activeReviewSessionId) {
    return {
      kind: 'navigate',
      label: '틀린 문제 복습하기',
      path: `/review-sessions/${candidate.activeReviewSessionId}`,
    }
  }
  return {
    kind: 'start-review',
    label: '틀린 문제 복습하기',
    sourceAttemptId: candidate.sourceAttemptId,
  }
}

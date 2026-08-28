import type {
  LatestReview,
  PendingSelfAssessment,
  QuizSetSummary,
} from '../api/quiz.types'

export type QuizManagementAction = { label: string; path: string; primary?: boolean }
export type QuizManagementActionState = 'loading' | 'ready' | 'error'

export function resolveQuizManagementActionState(
  pendingState: QuizManagementActionState,
  latestReviewState: QuizManagementActionState,
): QuizManagementActionState {
  if (pendingState === 'error' || latestReviewState === 'error') return 'error'
  if (pendingState === 'loading' || latestReviewState === 'loading') return 'loading'
  return 'ready'
}

export function resolveQuizManagementActions(
  quiz: QuizSetSummary,
  pending: PendingSelfAssessment | null,
  latestReview?: LatestReview,
): QuizManagementAction[] {
  if (quiz.status === 'GENERATING') return []
  if (quiz.status === 'FAILED') {
    return [{ label: '생성 다시 시도', path: `/learning/${quiz.materialId}/quiz`, primary: true }]
  }
  if (pending) {
    return [{ label: '자기평가 이어하기', path: `/quiz-sets/${pending.quizSetId}`, primary: true }]
  }
  if (latestReview?.quizSetId === quiz.quizSetId && latestReview.sourceAttemptId) {
    const actions: QuizManagementAction[] = [
      { label: '결과 보기', path: `/quiz-attempts/${latestReview.sourceAttemptId}/result` },
    ]
    if (latestReview.activeReviewSessionId) {
      actions.push({ label: '복습 이어하기', path: `/review-sessions/${latestReview.activeReviewSessionId}`, primary: true })
    } else if (latestReview.reviewQuestionCount > 0) {
      actions.push({ label: `틀린 문제 ${latestReview.reviewQuestionCount}개 복습하기`, path: '/review', primary: true })
    } else {
      actions.push({ label: '전체 다시 풀기', path: `/quiz-sets/${quiz.quizSetId}`, primary: true })
    }
    return actions
  }
  return [
    {
      label: quiz.lastAttemptAt ? '전체 다시 풀기' : '퀴즈 풀기',
      path: `/quiz-sets/${quiz.quizSetId}`,
      primary: true,
    },
  ]
}

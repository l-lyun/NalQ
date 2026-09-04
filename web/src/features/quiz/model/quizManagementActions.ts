import type { PendingSelfAssessment } from '../api/quiz.types'

export function parseExpandedQuizIds(value: string | null) {
  return new Set((value ?? '').split(',').filter(Boolean))
}

export function toggleExpandedQuizId(expanded: ReadonlySet<string>, quizSetId: string) {
  const next = new Set(expanded)
  if (next.has(quizSetId)) next.delete(quizSetId)
  else next.add(quizSetId)
  return next
}

export function createNewMainQuizDestination(quizSetId: string) {
  return {
    path: `/quiz-sets/${quizSetId}`,
    state: { restartMain: true as const },
  }
}

export function resolvePendingSelfAssessmentForQuizEntry(
  pending: PendingSelfAssessment | null,
  restartMain: boolean,
) {
  return restartMain ? null : pending
}

export function resolveQuizSetInitialScene(
  status: 'GENERATING' | 'READY' | 'FAILED',
  pending: { attemptId: string } | null,
  restartMain: boolean,
) {
  if (pending) return 'SELF_ASSESSMENT' as const
  if (status !== 'READY') return 'GENERATION' as const
  return restartMain ? 'SOLVING' as const : 'READY' as const
}

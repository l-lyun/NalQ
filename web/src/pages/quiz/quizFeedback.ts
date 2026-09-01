import type { QuizResult, QuizResultOutcome } from './quiz.types'

export function getQuizResultTitle(kind: QuizResult['kind']) {
  return kind === 'REVIEW' ? '복습 결과' : '채점 결과'
}

export function getQuizOutcomeLabel(outcome?: QuizResultOutcome) {
  if (outcome === 'CORRECT') return '정답'
  if (outcome === 'PARTIAL') return '보완 필요'
  if (outcome === 'INCORRECT') return '오답'
  return '평가 대기'
}

export function shouldShowQuizReviewAction(result: QuizResult) {
  return result.reviewAvailable
}

import type { CreateQuizSetRequest } from '@/features/quiz/api/quiz.types'
import type { QuizConditions, QuizGenerationFailure } from '@/pages/quiz/quiz.types'

export const GENERATION_PROMPT_MAX_CODE_POINTS = 300

export function countGenerationPromptCodePoints(value: string) {
  return Array.from(value).length
}

export function trimGenerationPrompt(value: string) {
  return value.trim()
}

export function sliceGenerationPrompt(value: string) {
  return Array.from(value).slice(0, GENERATION_PROMPT_MAX_CODE_POINTS).join('')
}

export function isQuizGenerationActiveConflict(error: unknown) {
  return typeof error === 'object'
    && error !== null
    && 'code' in error
    && error.code === 'QUIZ_001'
}

export function getQuizGenerationRecoveryMode(failure: QuizGenerationFailure) {
  if (failure.kind === 'STATUS_UNAVAILABLE') return 'REFRESH_STATUS' as const
  if (failure.kind === 'REQUEST_FAILED' && failure.retryable) return 'RETRY_REQUEST' as const
  return 'RETURN_TO_CONDITIONS' as const
}

export function toCreateQuizSetRequest(conditions: QuizConditions): CreateQuizSetRequest {
  const generationPrompt = trimGenerationPrompt(conditions.generationPrompt ?? '')
  return {
    selectedTypes: conditions.selectedTypes,
    difficulty: conditions.difficulty,
    maxQuestionCount: conditions.maxQuestionCount,
    ...(generationPrompt ? { generationPrompt } : {}),
  }
}

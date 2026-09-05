import type { QuizRequestedConfig } from '@/pages/quiz/quiz.types'

const PREFIX = 'openmd.quiz.requested-config.v1'
const types = new Set(['MULTIPLE_CHOICE', 'FILL_IN_THE_BLANK', 'SHORT_ANSWER', 'ESSAY'])
const difficulties = new Set(['EASY', 'NORMAL', 'HARD'])
const counts = new Set([5, 10, 15, 20])

function key(userId: number, quizSetId: string) {
  return `${PREFIX}:${userId}:${quizSetId}`
}

function isConfig(value: unknown): value is QuizRequestedConfig {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<QuizRequestedConfig>
  return (
    Array.isArray(candidate.selectedTypes) &&
    candidate.selectedTypes.length > 0 &&
    candidate.selectedTypes.every((type) => types.has(type)) &&
    typeof candidate.difficulty === 'string' &&
    difficulties.has(candidate.difficulty) &&
    typeof candidate.maxQuestionCount === 'number' &&
    counts.has(candidate.maxQuestionCount)
  )
}

export function saveRequestedConfig(
  userId: number,
  quizSetId: string,
  requestedConfig: QuizRequestedConfig & { generationPrompt?: string },
) {
  try {
    const displayConfig: QuizRequestedConfig = {
      selectedTypes: requestedConfig.selectedTypes,
      difficulty: requestedConfig.difficulty,
      maxQuestionCount: requestedConfig.maxQuestionCount,
    }
    localStorage.setItem(key(userId, quizSetId), JSON.stringify(displayConfig))
  } catch {
    // Local display metadata is optional and must not block generation.
  }
}

export function loadRequestedConfig(userId: number, quizSetId: string) {
  try {
    const raw = localStorage.getItem(key(userId, quizSetId))
    if (!raw) return undefined
    const parsed: unknown = JSON.parse(raw)
    if (isConfig(parsed)) return parsed
    localStorage.removeItem(key(userId, quizSetId))
  } catch {
    // Treat unavailable or corrupt storage as absent.
  }
  return undefined
}

export function clearRequestedConfigs(userId: number) {
  try {
    const userPrefix = `${PREFIX}:${userId}:`
    for (let index = localStorage.length - 1; index >= 0; index -= 1) {
      const storageKey = localStorage.key(index)
      if (storageKey?.startsWith(userPrefix)) localStorage.removeItem(storageKey)
    }
  } catch {
    // Optional local metadata cleanup must not block logout.
  }
}

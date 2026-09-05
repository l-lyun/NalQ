import { resolveQuizRuntimeMode } from './quizRuntimeMode'

export type { QuizRuntimeMode } from './quizRuntimeMode'

/** Normal development and production use the server. Fixtures require `pnpm dev:mock`. */
export const quizRuntimeMode = resolveQuizRuntimeMode(
  import.meta.env.VITE_QUIZ_RUNTIME_MODE,
  import.meta.env.DEV,
)

export const quizApiEnabled = quizRuntimeMode === 'api'

export const quizMockEnabled = quizRuntimeMode === 'mock'
export const quizRoutesEnabled = true

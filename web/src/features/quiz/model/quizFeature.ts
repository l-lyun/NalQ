export type QuizRuntimeMode = 'api' | 'mock' | 'disabled'

export const quizApiEnabled = import.meta.env.VITE_QUIZ_API_ENABLED === 'true'

/**
 * Local development keeps the complete learning journey reviewable even while
 * the quiz server APIs are disabled. Production never falls back to fixture
 * data: it either uses the explicitly enabled API or leaves quiz routes off.
 */
export const quizRuntimeMode: QuizRuntimeMode = quizApiEnabled
  ? 'api'
  : import.meta.env.DEV
    ? 'mock'
    : 'disabled'

export const quizMockEnabled = quizRuntimeMode === 'mock'
export const quizRoutesEnabled = quizRuntimeMode !== 'disabled'

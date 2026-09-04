export type QuizRuntimeMode = 'api' | 'mock' | 'disabled'

export function resolveQuizRuntimeMode(
  configuredMode: string | undefined,
  development: boolean,
): QuizRuntimeMode {
  return configuredMode === 'mock' && development ? 'mock' : 'api'
}

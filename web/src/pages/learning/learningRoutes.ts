export type LearningRouteId = 'main' | 'materials' | 'material-edit' | 'quizzes' | 'new-quiz'

export type LearningRoute =
  | { id: 'material-edit'; materialId: string }
  | { id: Exclude<LearningRouteId, 'material-edit'> }

export function resolveLearningRoute(pathname: string): LearningRoute {
  const normalized = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  const materialMatch = normalized.match(/^\/learning\/materials\/([^/]+)$/)
  if (materialMatch?.[1]) {
    return { id: 'material-edit', materialId: decodeURIComponent(materialMatch[1]) }
  }
  if (normalized === '/learning/materials') return { id: 'materials' }
  if (normalized === '/learning/quizzes') return { id: 'quizzes' }
  if (normalized === '/learning/new') return { id: 'new-quiz' }
  return { id: 'main' }
}

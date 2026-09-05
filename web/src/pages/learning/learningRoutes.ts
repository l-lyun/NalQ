export type LearningRouteId =
  | 'main'
  | 'materials'
  | 'material-edit'
  | 'material-create'
  | 'notion-import'
  | 'quizzes'
  | 'new-quiz'

export type LearningRoute =
  | { id: 'material-edit'; materialId: string }
  | { id: Exclude<LearningRouteId, 'material-edit'> }

export function resolveLearningRoute(pathname: string): LearningRoute {
  const normalized = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
  if (normalized === '/learning/materials/new') return { id: 'material-create' }
  const materialMatch = normalized.match(/^\/learning\/materials\/([^/]+)$/)
  if (materialMatch?.[1]) {
    return { id: 'material-edit', materialId: decodeURIComponent(materialMatch[1]) }
  }
  if (normalized === '/learning/materials') return { id: 'materials' }
  if (normalized === '/learning/import/notion') return { id: 'notion-import' }
  if (normalized === '/learning/quizzes') return { id: 'quizzes' }
  if (normalized === '/learning/new') return { id: 'new-quiz' }
  return { id: 'main' }
}

export function getLearningRoutePanelClassName(screenId: string) {
  return screenId === 'main'
    ? 'learning-route-panel'
    : 'learning-route-panel learning-route-panel--enter'
}

export function resolveLearningMaterialsReturnTo(value: unknown) {
  if (typeof value !== 'string') return undefined

  try {
    const base = 'https://openmd.local'
    const target = new URL(value, base)
    const pathname = target.pathname.length > 1
      ? target.pathname.replace(/\/+$/, '')
      : target.pathname

    if (target.origin !== base || pathname !== '/learning/materials' || target.hash) {
      return undefined
    }

    return `/learning/materials${target.search}`
  } catch {
    return undefined
  }
}

export function resolveLearningQuizzesReturnTo(value: unknown) {
  if (typeof value !== 'string') return undefined

  try {
    const base = 'https://openmd.local'
    const target = new URL(value, base)
    const pathname = target.pathname.length > 1
      ? target.pathname.replace(/\/+$/, '')
      : target.pathname

    if (target.origin !== base || pathname !== '/learning/quizzes' || target.hash) {
      return undefined
    }

    return `/learning/quizzes${target.search}`
  } catch {
    return undefined
  }
}

export function readLearningCreateReturnState(value: unknown) {
  if (!value || typeof value !== 'object') return {}
  const candidate = value as { returnTo?: unknown; returnScrollTop?: unknown }
  const returnTo = resolveLearningQuizzesReturnTo(candidate.returnTo)
  const returnScrollTop = typeof candidate.returnScrollTop === 'number'
    && Number.isFinite(candidate.returnScrollTop)
    && candidate.returnScrollTop >= 0
    ? candidate.returnScrollTop
    : undefined

  return {
    ...(returnTo ? { returnTo } : {}),
    ...(returnTo && returnScrollTop !== undefined ? { returnScrollTop } : {}),
  }
}

export function resolveLearningEditorBackNavigation(
  _sourceType: 'NOTION' | 'PASTE',
  returnTo: string | undefined,
  _learningCreateReturnState: ReturnType<typeof readLearningCreateReturnState>,
) {
  return {
    to: returnTo ?? '/learning/new',
    replace: true as const,
  }
}

export function resolveLearningMaterialEditBackNavigation(returnToValue: unknown) {
  return {
    to: resolveLearningMaterialsReturnTo(returnToValue) ?? '/learning/materials',
    replace: true as const,
  }
}

export function shouldCommitLearningSearchInput(
  eventIsComposing: boolean,
  compositionActive: boolean,
) {
  return !eventIsComposing && !compositionActive
}

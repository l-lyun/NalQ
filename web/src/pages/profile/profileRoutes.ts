export const profileSubPageNavigationState = { fromProfile: true } as const

export function normalizeProfilePath(pathname: string) {
  return pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname
}

export function cameFromProfileMain(state: unknown) {
  return Boolean(
    state
    && typeof state === 'object'
    && 'fromProfile' in state
    && state.fromProfile === true,
  )
}

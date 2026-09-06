export type AuthContext = Readonly<{ authEpoch: number; userId: number | null }>

let current: AuthContext = { authEpoch: 0, userId: null }
let refreshAllowed = true
const listeners = new Set<() => void>()

export class AuthContextChangedError extends Error {
  readonly code = 'AUTH_CONTEXT_CHANGED'
  constructor() { super('Authentication context changed.'); this.name = 'AuthContextChangedError' }
}

export function getAuthContext(): AuthContext { return { ...current } }

export function isCurrentAuthContext(context: AuthContext) {
  return current.authEpoch === context.authEpoch && current.userId === context.userId
}

export function assertAuthContext(context: AuthContext) {
  if (!isCurrentAuthContext(context)) throw new AuthContextChangedError()
}

/** Identity transitions advance the epoch; an ordinary access token refresh must not. */
export function advanceAuthContext(userId: number | null = null, allowRefresh = true): AuthContext {
  refreshAllowed = allowRefresh
  current = { authEpoch: current.authEpoch + 1, userId }
  listeners.forEach((listener) => listener())
  return getAuthContext()
}

export function assertAuthRefreshAllowed() {
  if (!refreshAllowed) throw new AuthContextChangedError()
}

export function subscribeAuthContext(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

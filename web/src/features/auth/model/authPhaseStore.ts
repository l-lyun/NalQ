export type AuthPhase = 'bootstrapping' | 'bootstrap-error' | 'authenticated' | 'anonymous'

let authPhase: AuthPhase = 'bootstrapping'
const listeners = new Set<() => void>()

export function getAuthPhase() {
  return authPhase
}

export function setAuthPhase(nextPhase: AuthPhase) {
  if (authPhase === nextPhase) return

  authPhase = nextPhase
  listeners.forEach((listener) => listener())
}

export function subscribeAuthPhase(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

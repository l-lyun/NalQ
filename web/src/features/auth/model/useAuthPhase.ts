import { useSyncExternalStore } from 'react'

import { getAuthPhase, subscribeAuthPhase } from './authPhaseStore'

export function useAuthPhase() {
  return useSyncExternalStore(subscribeAuthPhase, getAuthPhase, getAuthPhase)
}

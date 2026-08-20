import { refreshSessionTransport } from '@/features/auth/api/authTransport'
import { ApiClientError } from '@/shared/api/apiError'

import { endLocalSession } from './sessionCleanup'
import { setSessionTokens } from './tokenVault'

let refreshPromise: Promise<void> | null = null
const REFRESH_LOCK_NAME = 'openmd-browser-session-refresh'

async function executeRefresh() {
  const tokens = await refreshSessionTransport()
  setSessionTokens(tokens)
}

function executeRefreshWithCrossTabLock() {
  if (!navigator.locks) return executeRefresh()

  return navigator.locks.request(REFRESH_LOCK_NAME, { mode: 'exclusive' }, executeRefresh)
}

export function refreshAccessToken() {
  if (refreshPromise) return refreshPromise

  refreshPromise = executeRefreshWithCrossTabLock()
    .catch(async (error: unknown) => {
      if (error instanceof ApiClientError && error.code === 'AUTH_005') {
        await endLocalSession()
      }
      throw error
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

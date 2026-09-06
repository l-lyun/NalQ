import { refreshSessionTransport } from '@/features/auth/api/authTransport'
import { ApiClientError } from '@/shared/api/apiError'

import { endLocalSession } from './sessionCleanup'
import { setSessionTokens } from './tokenVault'
import { assertAuthContext, assertAuthRefreshAllowed, getAuthContext, isCurrentAuthContext, type AuthContext } from './authContext'

let refreshOperation: { context: AuthContext; promise: Promise<void> } | null = null
const REFRESH_LOCK_NAME = 'openmd-browser-session-refresh'

async function executeRefresh(context: AuthContext) {
  assertAuthRefreshAllowed()
  assertAuthContext(context)
  const tokens = await refreshSessionTransport()
  assertAuthContext(context)
  setSessionTokens(tokens)
}

function executeRefreshWithCrossTabLock(context: AuthContext) {
  if (!navigator.locks) return executeRefresh(context)

  return navigator.locks.request(REFRESH_LOCK_NAME, { mode: 'exclusive' }, () => executeRefresh(context))
}

export function refreshAccessToken() {
  try { assertAuthRefreshAllowed() } catch (error) { return Promise.reject(error) }
  const context = getAuthContext()
  if (refreshOperation && isCurrentAuthContext(refreshOperation.context)) return refreshOperation.promise

  const promise = executeRefreshWithCrossTabLock(context)
    .catch(async (error: unknown) => {
      if (isCurrentAuthContext(context) && error instanceof ApiClientError && error.code === 'AUTH_005') {
        await endLocalSession()
      }
      throw error
    })
    .finally(() => {
      if (refreshOperation?.promise === promise) refreshOperation = null
    })

  refreshOperation = { context, promise }
  return promise
}

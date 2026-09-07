import { queryClient } from '@/app/providers/queryClient'
import { createSession } from '@/features/auth/api/auth.api'
import {
  completeBrowserSignUpTransport,
  logoutSessionTransport,
} from '@/features/auth/api/authTransport'
import type { CompleteSignUpRequest, CurrentUser, LoginRequest } from '@/features/auth/api/auth.types'
import { ApiClientError } from '@/shared/api/apiError'
import { prepareAutomaticOnboarding } from '@/features/onboarding/model/automaticOnboarding'

import { currentUserQueryOptions } from './auth.queries'
import { refreshAccessToken } from './authRefresh'
import { getAuthPhase, setAuthPhase } from './authPhaseStore'
import { broadcastSessionEnded } from './authSessionChannel'
import { clearAuthAndPrivateCaches, endLocalSession } from './sessionCleanup'
import { clearSessionTokens, setSessionTokens } from './tokenVault'
import { advanceAuthContext, assertAuthContext, getAuthContext, isCurrentAuthContext } from './authContext'
import { prepareNativeSessionEnd } from '@/shared/native/nativeSessionLifecycle'

let bootstrapPromise: Promise<void> | null = null

export async function loginAndLoadCurrentUser(payload: LoginRequest) {
  const context = advanceAuthContext(null, false)
  clearSessionTokens()
  const tokens = await createSession(payload)
  assertAuthContext(context)
  setSessionTokens(tokens)
  await clearAuthAndPrivateCaches()
  assertAuthContext(context)
  return completeCurrentUserSession()
}

export async function completeSignUpAndLoadCurrentUser(payload: CompleteSignUpRequest) {
  const context = advanceAuthContext(null, false)
  clearSessionTokens()
  const tokens = await completeBrowserSignUpTransport(payload)
  assertAuthContext(context)
  setSessionTokens(tokens)
  await clearAuthAndPrivateCaches()
  assertAuthContext(context)
  return completeNewUserSession()
}

export async function recoverCompletedSignUpSession() {
  const context = advanceAuthContext()
  await refreshAccessToken()
  assertAuthContext(context)
  await clearAuthAndPrivateCaches()
  assertAuthContext(context)
  return completeNewUserSession()
}

export async function completeCurrentUserSession() {
  return loadAndActivateCurrentUser()
}

async function completeNewUserSession() {
  return loadAndActivateCurrentUser((user) => {
    prepareAutomaticOnboarding(user.id)
  })
}

async function loadAndActivateCurrentUser(onLoaded?: (user: CurrentUser) => void) {
  const context = getAuthContext()
  try {
    const user = await queryClient.fetchQuery(currentUserQueryOptions)
    assertAuthContext(context)
    onLoaded?.(user)
    advanceAuthContext(user.id)
    setAuthPhase('authenticated')
    return user
  } catch (error) {
    if (isCurrentAuthContext(context) && error instanceof ApiClientError && error.code === 'AUTH_005') {
      await endLocalSession()
    }
    throw error
  }
}

export async function logoutCurrentSession() {
  const pendingEnd = prepareNativeSessionEnd('LOGOUT')
  const context = advanceAuthContext(null, false)
  clearSessionTokens()
  try {
    await pendingEnd
    assertAuthContext(context)
    await logoutSessionTransport()
  } finally {
    if (isCurrentAuthContext(context)) {
      try {
        await endLocalSession()
      } finally {
        broadcastSessionEnded()
        window.location.replace('/')
      }
    }
  }
}

export async function completeAccountWithdrawal() {
  const pendingEnd = prepareNativeSessionEnd('WITHDRAWAL')
  const context = advanceAuthContext(null, false)
  clearSessionTokens()
  await pendingEnd
  if (!isCurrentAuthContext(context)) return
  try {
    await endLocalSession()
  } finally {
    broadcastSessionEnded()
  }
}

async function executeBootstrap() {
  const context = getAuthContext()
  try {
    await refreshAccessToken()
    assertAuthContext(context)
    const user = await queryClient.fetchQuery(currentUserQueryOptions)
    assertAuthContext(context)
    advanceAuthContext(user.id)
    setAuthPhase('authenticated')
  } catch (error) {
    if (!isCurrentAuthContext(context)) return
    if (error instanceof ApiClientError && error.code === 'AUTH_005') {
      await endLocalSession()
      return
    }

    setAuthPhase('bootstrap-error')
  }
}

export function bootstrapAuthSession() {
  const phase = getAuthPhase()
  if (phase !== 'bootstrapping' && phase !== 'bootstrap-error') return Promise.resolve()
  if (bootstrapPromise) return bootstrapPromise

  setAuthPhase('bootstrapping')
  bootstrapPromise = executeBootstrap().finally(() => {
    bootstrapPromise = null
  })
  return bootstrapPromise
}

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
import { setSessionTokens } from './tokenVault'

let bootstrapPromise: Promise<void> | null = null

export async function loginAndLoadCurrentUser(payload: LoginRequest) {
  const tokens = await createSession(payload)
  setSessionTokens(tokens)
  await clearAuthAndPrivateCaches()
  return completeCurrentUserSession()
}

export async function completeSignUpAndLoadCurrentUser(payload: CompleteSignUpRequest) {
  const tokens = await completeBrowserSignUpTransport(payload)
  setSessionTokens(tokens)
  await clearAuthAndPrivateCaches()
  return completeNewUserSession()
}

export async function recoverCompletedSignUpSession() {
  await refreshAccessToken()
  await clearAuthAndPrivateCaches()
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
  try {
    const user = await queryClient.fetchQuery(currentUserQueryOptions)
    onLoaded?.(user)
    setAuthPhase('authenticated')
    return user
  } catch (error) {
    if (error instanceof ApiClientError && error.code === 'AUTH_005') {
      await endLocalSession()
    }
    throw error
  }
}

export async function logoutCurrentSession() {
  try {
    await logoutSessionTransport()
  } finally {
    try {
      await endLocalSession()
    } finally {
      broadcastSessionEnded()
      window.location.replace('/')
    }
  }
}

export async function completeAccountWithdrawal() {
  try {
    await endLocalSession()
  } finally {
    broadcastSessionEnded()
  }
}

async function executeBootstrap() {
  try {
    await refreshAccessToken()
    await queryClient.fetchQuery(currentUserQueryOptions)
    setAuthPhase('authenticated')
  } catch (error) {
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

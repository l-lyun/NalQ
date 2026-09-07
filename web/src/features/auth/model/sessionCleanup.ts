import { queryClient } from '@/app/providers/queryClient'
import type { CurrentUser } from '@/features/auth/api/auth.types'
import { clearRequestedConfigs } from '@/features/quiz/model/quizRequestedConfigStorage'

import { setAuthPhase } from './authPhaseStore'
import { clearSessionTokens } from './tokenVault'
import { advanceAuthContext, isCurrentAuthContext } from './authContext'

async function cancelAuthAndPrivateQueries() {
  await Promise.all([
    queryClient.cancelQueries({ queryKey: ['auth', 'me'], exact: true }),
    queryClient.cancelQueries({ queryKey: ['private'] }),
  ])
}

function removeAuthAndPrivateCaches() {
  queryClient.removeQueries({ queryKey: ['auth', 'me'], exact: true })
  queryClient.removeQueries({ queryKey: ['private'] })
}

export async function clearAuthAndPrivateCaches() {
  await cancelAuthAndPrivateQueries()
  removeAuthAndPrivateCaches()
}

export async function endLocalSession() {
  const user = queryClient.getQueryData<CurrentUser>(['auth', 'me'])
  const context = advanceAuthContext(null, false)
  clearSessionTokens()
  setAuthPhase('anonymous')
  await cancelAuthAndPrivateQueries()
  if (!isCurrentAuthContext(context)) return
  if (user) clearRequestedConfigs(user.id)
  removeAuthAndPrivateCaches()
}

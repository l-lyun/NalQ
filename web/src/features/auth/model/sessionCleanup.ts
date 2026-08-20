import { queryClient } from '@/app/providers/queryClient'

import { setAuthPhase } from './authPhaseStore'
import { clearSessionTokens } from './tokenVault'

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
  await cancelAuthAndPrivateQueries()
  clearSessionTokens()
  removeAuthAndPrivateCaches()
  setAuthPhase('anonymous')
}

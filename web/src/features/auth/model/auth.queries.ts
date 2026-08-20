import { queryOptions, useQuery } from '@tanstack/react-query'

import { shouldRetryQuery } from '@/app/providers/queryClient'
import { getCurrentUser } from '@/features/auth/api/auth.api'

import { useAuthPhase } from './useAuthPhase'

export const currentUserQueryKey = ['auth', 'me'] as const

export const currentUserQueryOptions = queryOptions({
  queryKey: currentUserQueryKey,
  queryFn: ({ signal }) => getCurrentUser(signal),
  staleTime: 5 * 60 * 1000,
  gcTime: Number.POSITIVE_INFINITY,
  refetchOnWindowFocus: true,
  refetchOnReconnect: 'always',
  refetchInterval: false,
  retry: shouldRetryQuery,
})

export function useCurrentUser() {
  const phase = useAuthPhase()
  return useQuery({
    ...currentUserQueryOptions,
    enabled: phase === 'authenticated',
  })
}

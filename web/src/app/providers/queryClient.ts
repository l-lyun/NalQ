import { QueryClient } from '@tanstack/react-query'

import { ApiClientError } from '@/shared/api/apiError'

export function shouldRetryQuery(failureCount: number, error: unknown) {
  if (failureCount >= 1) return false
  if (!(error instanceof ApiClientError)) return false
  if (error.code === 'AUTH_005') return false
  if (error.status !== undefined && error.status >= 400 && error.status < 500) return false

  return error.kind === 'network' || (error.status !== undefined && error.status >= 500)
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetryQuery,
    },
    mutations: {
      retry: false,
    },
  },
})

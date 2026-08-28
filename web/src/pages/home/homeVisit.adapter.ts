import { queryOptions } from '@tanstack/react-query'

import type { ApiResponse } from '@/features/auth/api/auth.types'
import { unwrapApiResponse } from '@/shared/api/apiError'
import { protectedApi } from '@/shared/api/protectedApi'

export type HomeVisitSummary = {
  visitDate: string
  consecutiveVisitDays: number
}

type HomeVisitResult =
  | { source: 'api'; summary: HomeVisitSummary }
  | { source: 'mock-unavailable'; summary: null }

const homeVisitsApiEnabled = import.meta.env.VITE_HOME_VISITS_API_ENABLED === 'true'

async function recordHomeVisit(signal?: AbortSignal): Promise<HomeVisitResult> {
  if (!homeVisitsApiEnabled) {
    // The endpoint is not on dev yet. This explicit adapter never invents a visit
    // count or presents fixture data as a server response.
    return { source: 'mock-unavailable', summary: null }
  }

  const response = await protectedApi.post<ApiResponse<HomeVisitSummary>>(
    '/api/v1/home-visits',
    undefined,
    { signal },
  )
  return { source: 'api', summary: unwrapApiResponse(response.data) }
}

export const homeVisitQueryOptions = queryOptions({
  queryKey: ['private', 'home-visits', 'today'] as const,
  queryFn: ({ signal }) => recordHomeVisit(signal),
  staleTime: 5 * 60 * 1_000,
  retry: false,
  refetchOnWindowFocus: false,
})

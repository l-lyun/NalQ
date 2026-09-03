import { queryOptions } from '@tanstack/react-query'

import type { ApiResponse } from '@/features/auth/api/auth.types'
import { unwrapApiResponse } from '@/shared/api/apiError'
import { protectedApi } from '@/shared/api/protectedApi'

import type {
  NotionAuthorization,
  NotionConnection,
  NotionImportResult,
  NotionPageBatch,
} from './notionImport.types'

export const notionImportKeys = {
  all: ['private', 'notion-import'] as const,
  connection: () => [...notionImportKeys.all, 'connection'] as const,
  pages: (query: string) => [...notionImportKeys.all, 'pages', query] as const,
}

export const notionConnectionQueryOptions = queryOptions({
  queryKey: notionImportKeys.connection(),
  queryFn: ({ signal }) => getNotionConnection(signal),
  staleTime: 0,
  refetchOnWindowFocus: false,
})

export async function getNotionConnection(signal?: AbortSignal) {
  const response = await protectedApi.get<ApiResponse<NotionConnection>>(
    '/api/v1/integrations/notion/connection',
    { signal },
  )
  return unwrapApiResponse(response.data)
}

export async function startNotionAuthorization(returnUri: string) {
  const response = await protectedApi.post<ApiResponse<NotionAuthorization>>(
    '/api/v1/integrations/notion/authorizations',
    { returnUri },
  )
  return unwrapApiResponse(response.data)
}

export async function getNotionPages(
  { query, cursor }: { query: string; cursor?: string },
  signal?: AbortSignal,
) {
  const response = await protectedApi.get<ApiResponse<NotionPageBatch>>(
    '/api/v1/integrations/notion/pages',
    {
      signal,
      timeout: 25_000,
      params: {
        ...(query.trim() ? { query: query.trim() } : {}),
        ...(cursor ? { cursor } : {}),
      },
    },
  )
  return unwrapApiResponse(response.data)
}

export async function disconnectNotion() {
  const response = await protectedApi.delete<ApiResponse<{ status: 'DISCONNECTED' }>>(
    '/api/v1/integrations/notion/connection',
  )
  return unwrapApiResponse(response.data)
}

export async function importNotionPage(pageId: string) {
  const response = await protectedApi.post<ApiResponse<NotionImportResult>>(
    '/api/v1/learning-material-imports/notion',
    { pageId },
    { timeout: 30_000 },
  )
  return unwrapApiResponse(response.data)
}

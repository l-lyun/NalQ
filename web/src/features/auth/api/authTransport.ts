import type { ApiResponse, LoginRequest, SessionTokens } from './auth.types'
import { publicApi } from '@/shared/api/publicApi'
import { toApiClientError, unwrapApiResponse } from '@/shared/api/apiError'

const browserSessionConfig = {
  withCredentials: true,
  headers: {
    'X-OpenMD-CSRF': '1',
  },
} as const

export async function createBrowserSessionTransport(
  payload: LoginRequest,
): Promise<SessionTokens> {
  try {
    const response = await publicApi.post<ApiResponse<SessionTokens>>(
      '/api/v1/auth/web/sessions',
      payload,
      browserSessionConfig,
    )
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

export async function refreshSessionTransport(): Promise<SessionTokens> {
  try {
    const response = await publicApi.post<ApiResponse<SessionTokens>>(
      '/api/v1/auth/web/sessions/refresh',
      undefined,
      browserSessionConfig,
    )
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

export async function logoutSessionTransport(): Promise<void> {
  try {
    const response = await publicApi.delete<ApiResponse<null>>(
      '/api/v1/auth/web/sessions/current',
      browserSessionConfig,
    )
    unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

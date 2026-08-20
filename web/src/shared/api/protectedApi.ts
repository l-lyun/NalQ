import axios, { type InternalAxiosRequestConfig } from 'axios'

import type { ApiResponse } from '@/features/auth/api/auth.types'
import { refreshAccessToken } from '@/features/auth/model/authRefresh'
import {
  getAccessToken,
  shouldRefreshAccessToken,
} from '@/features/auth/model/tokenVault'

import { ApiClientError, toApiClientError } from './apiError'

type AuthRetryConfig = InternalAxiosRequestConfig & {
  _authRetried?: boolean
}

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const protectedApi = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10_000,
})

protectedApi.interceptors.request.use(async (config) => {
  if (shouldRefreshAccessToken()) await refreshAccessToken()

  const accessToken = getAccessToken()
  if (!accessToken) {
    throw new ApiClientError({
      message: '로그인이 필요해요.',
      code: 'AUTH_005',
      kind: 'api',
      status: 401,
    })
  }

  config.headers.set('Authorization', `Bearer ${accessToken}`)
  return config
})

protectedApi.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError<ApiResponse<unknown>>(error) || !error.config) {
      throw toApiClientError(error)
    }

    const apiError = toApiClientError(error)
    const config = error.config as AuthRetryConfig
    const isExpiredAccess = error.response?.status === 401 && apiError.code === 'AUTH_005'

    if (!isExpiredAccess || config._authRetried) throw apiError

    config._authRetried = true
    await refreshAccessToken()

    const accessToken = getAccessToken()
    if (!accessToken) throw apiError

    config.headers.set('Authorization', `Bearer ${accessToken}`)
    return protectedApi(config)
  },
)

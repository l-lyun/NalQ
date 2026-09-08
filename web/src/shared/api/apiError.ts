import axios from 'axios'
import { AuthContextChangedError } from '@/features/auth/model/authContext'

import type { ApiErrorBody, ApiResponse } from '@/features/auth/api/auth.types'

export type ApiErrorKind = 'api' | 'network' | 'unknown'

export class ApiClientError extends Error {
  readonly code: string | undefined
  readonly fields: ApiErrorBody['fields']
  readonly kind: ApiErrorKind
  readonly status: number | undefined
  readonly retryAfterMs: number | undefined

  constructor(options: {
    message: string
    code?: string
    fields?: ApiErrorBody['fields']
    kind: ApiErrorKind
    status?: number
    retryAfterMs?: number
  }) {
    super(options.message)
    this.name = 'ApiClientError'
    this.code = options.code
    this.fields = options.fields ?? []
    this.kind = options.kind
    this.status = options.status
    this.retryAfterMs = options.retryAfterMs
  }
}

export function unwrapApiResponse<T>(response: ApiResponse<T>): T {
  if (response.success) return response.data

  throw new ApiClientError({
    message: response.error.message,
    code: response.error.code,
    fields: response.error.fields,
    kind: 'api',
  })
}

export function toApiClientError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) return error
  if (error instanceof AuthContextChangedError) {
    return new ApiClientError({ message: '인증 상태가 변경됐어요.', code: error.code, kind: 'api' })
  }

  if (!axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return new ApiClientError({
      message: '요청을 처리하지 못했어요.',
      kind: 'unknown',
    })
  }

  const body = error.response?.data
  const retryAfter = error.response?.headers?.['retry-after']
  const retryAfterMs = typeof retryAfter === 'string'
    ? Math.min(86_400_000, Math.max(0, /^\d+$/.test(retryAfter)
      ? Number(retryAfter) * 1000 : Date.parse(retryAfter) - Date.now()))
    : undefined
  if (body && !body.success && body.error) {
    return new ApiClientError({
      message: body.error.message,
      code: body.error.code,
      fields: body.error.fields,
      kind: 'api',
      status: error.response?.status,
      retryAfterMs: Number.isFinite(retryAfterMs) ? retryAfterMs : undefined,
    })
  }

  if (!error.response) {
    return new ApiClientError({
      message: '서버에 연결하지 못했어요. 네트워크 상태를 확인해 주세요.',
      kind: 'network',
    })
  }

  return new ApiClientError({
    message: '서버에서 요청을 처리하지 못했어요.',
    kind: 'api',
    status: error.response.status,
    retryAfterMs: Number.isFinite(retryAfterMs) ? retryAfterMs : undefined,
  })
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  const apiError = toApiClientError(error)
  return apiError.message || fallback
}

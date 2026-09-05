import axios from 'axios'

import type { ApiErrorBody, ApiResponse } from '@/features/auth/api/auth.types'

export type ApiErrorKind = 'api' | 'network' | 'unknown'

export class ApiClientError extends Error {
  readonly code: string | undefined
  readonly fields: ApiErrorBody['fields']
  readonly kind: ApiErrorKind
  readonly status: number | undefined

  constructor(options: {
    message: string
    code?: string
    fields?: ApiErrorBody['fields']
    kind: ApiErrorKind
    status?: number
  }) {
    super(options.message)
    this.name = 'ApiClientError'
    this.code = options.code
    this.fields = options.fields ?? []
    this.kind = options.kind
    this.status = options.status
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

  if (!axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return new ApiClientError({
      message: '요청을 처리하지 못했어요.',
      kind: 'unknown',
    })
  }

  const body = error.response?.data
  if (body && !body.success && body.error) {
    return new ApiClientError({
      message: body.error.message,
      code: body.error.code,
      fields: body.error.fields,
      kind: 'api',
      status: error.response?.status,
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
  })
}

export function getApiErrorMessage(error: unknown, fallback: string) {
  const apiError = toApiClientError(error)
  return apiError.message || fallback
}

import type {
  ApiResponse,
  AccountWithdrawalRequest,
  AccountWithdrawalResult,
  CurrentUser,
  EmailVerificationEmailRequest,
  EmailVerificationRequest,
  EmailVerified,
  LoginRequest,
  NicknameAvailability,
  NicknameAvailabilityRequest,
  UpdateCurrentUserNicknameRequest,
  VerificationRequired,
} from './auth.types'
import { protectedApi } from '@/shared/api/protectedApi'
import { publicApi } from '@/shared/api/publicApi'
import { toApiClientError, unwrapApiResponse } from '@/shared/api/apiError'

import { createBrowserSessionTransport } from './authTransport'

async function publicRequest<T>(request: () => Promise<{ data: ApiResponse<T> }>) {
  try {
    const response = await request()
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

export function requestVerificationEmail(payload: EmailVerificationEmailRequest) {
  return publicRequest<VerificationRequired>(() =>
    publicApi.post('/api/v1/auth/email-verifications', payload),
  )
}

export function resendVerificationEmail(email: string) {
  return publicRequest<VerificationRequired>(() =>
    publicApi.post('/api/v1/auth/email-verifications', { email }),
  )
}

export function confirmEmailVerification(payload: EmailVerificationRequest) {
  return publicRequest<EmailVerified>(() =>
    publicApi.post('/api/v1/auth/email-verifications/confirm', payload),
  )
}

export function checkNicknameAvailability(payload: NicknameAvailabilityRequest) {
  return publicRequest<NicknameAvailability>(() =>
    publicApi.post('/api/v1/auth/nickname-availability', payload),
  )
}

export function createSession(payload: LoginRequest) {
  return createBrowserSessionTransport(payload)
}

export async function getCurrentUser(signal?: AbortSignal) {
  try {
    const response = await protectedApi.get<ApiResponse<CurrentUser>>('/api/v1/users/me', { signal })
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

export async function updateCurrentUserNickname(payload: UpdateCurrentUserNicknameRequest) {
  try {
    const response = await protectedApi.patch<ApiResponse<CurrentUser>>('/api/v1/users/me', payload)
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

export async function withdrawCurrentUser(payload: AccountWithdrawalRequest) {
  try {
    const response = await protectedApi.delete<ApiResponse<AccountWithdrawalResult>>(
      '/api/v1/users/me',
      { data: payload, withCredentials: true },
    )
    return unwrapApiResponse(response.data)
  } catch (error) {
    throw toApiClientError(error)
  }
}

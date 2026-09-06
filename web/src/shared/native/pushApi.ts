import axios from 'axios'
import { assertAuthContext, type AuthContext } from '@/features/auth/model/authContext'
import { protectedApi } from '@/shared/api/protectedApi'
import { ApiClientError, toApiClientError, unwrapApiResponse } from '@/shared/api/apiError'
import type { ApiResponse } from '@/features/auth/api/auth.types'

export type Installation = { installationId: string; installationKey: string }
export type DeviceRequest = Installation & {
  operationId: string; operationIssuedAt: string; expectedRevision: number
  platform: 'IOS' | 'ANDROID'; permission: 'GRANTED' | 'DENIED'; pushToken?: string
}
export type RevokeRequest = Installation & {
  operationId: string; operationIssuedAt: string; bindingId: string; expectedRevision: number
}
export type DeviceState = {
  revision: number; belongsToCurrentUser: boolean; bindingId: string | null
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED'; platform: 'IOS' | 'ANDROID'
}
export type RegistrationResult = {
  installationId: string; revision: number; bindingId: string | null
  status: 'ACTIVE' | 'DISABLED' | 'REVOKED'; userId: number
}
export type PushResult<T> = { outcome: 'SUCCESS'; data: T }
  | { outcome: 'NOT_FOUND' }
  | { outcome: 'RETRY' | 'FAILED'; errorCode: string; retryAfterMs?: number }

const revokeApi = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  timeout: 10_000, withCredentials: false,
  headers: { 'Content-Type': 'application/json' },
})

function headers(installation: Installation) {
  return { 'X-Push-Installation-Key': installation.installationKey }
}
function path(installation: Installation) {
  return `/api/v1/push-devices/${encodeURIComponent(installation.installationId)}`
}
const uuid = (value: unknown): value is string => typeof value === 'string'
  && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
function validDevice(data: DeviceState | RegistrationResult) {
  return data && Number.isSafeInteger(data.revision) && data.revision >= 0
    && (data.bindingId === null || uuid(data.bindingId))
    && ['ACTIVE', 'DISABLED', 'REVOKED'].includes(data.status)
}
const invalidResponse = (): PushResult<never> => ({ outcome: 'FAILED', errorCode: 'PUSH_RESPONSE_INVALID' })

export function pushFailure(error: unknown): PushResult<never> {
  const failure = toApiClientError(error)
  const retry = failure.kind === 'network' || failure.status === 429 || failure.status === 401
    || (failure.status !== undefined && failure.status >= 500)
  return { outcome: retry ? 'RETRY' : 'FAILED', errorCode: failure.code ?? 'PUSH_REQUEST_FAILED',
    ...(failure.retryAfterMs !== undefined ? { retryAfterMs: failure.retryAfterMs } : {}) }
}

export async function getPushDevice(installation: Installation, context: AuthContext): Promise<PushResult<DeviceState>> {
  try {
    assertAuthContext(context)
    const response = await protectedApi.get<ApiResponse<DeviceState>>(path(installation), {
      headers: headers(installation), authContext: context,
    })
    assertAuthContext(context)
    const data = unwrapApiResponse(response.data)
    if (!validDevice(data) || typeof data.belongsToCurrentUser !== 'boolean'
      || !['IOS', 'ANDROID'].includes(data.platform)) return invalidResponse()
    return { outcome: 'SUCCESS', data: { revision: data.revision, belongsToCurrentUser: data.belongsToCurrentUser,
      bindingId: data.bindingId, status: data.status, platform: data.platform } }
  } catch (error) {
    if (error instanceof ApiClientError && error.status === 404) return { outcome: 'NOT_FOUND' }
    return pushFailure(error)
  }
}

export async function registerPushDevice(request: DeviceRequest, context: AuthContext): Promise<PushResult<RegistrationResult>> {
  try {
    assertAuthContext(context)
    const { operationId, operationIssuedAt, expectedRevision, platform, permission, pushToken } = request
    const response = await protectedApi.put<ApiResponse<RegistrationResult>>(path(request), {
      operationId, operationIssuedAt, expectedRevision, platform, permission, provider: 'EXPO',
      ...(permission === 'GRANTED' ? { pushToken } : {}),
    }, { headers: headers(request), authContext: context })
    assertAuthContext(context)
    const data = unwrapApiResponse(response.data)
    if (!validDevice(data) || data.userId !== context.userId || data.installationId !== request.installationId) {
      return invalidResponse()
    }
    return { outcome: 'SUCCESS', data: { installationId: data.installationId, revision: data.revision,
      bindingId: data.bindingId, status: data.status, userId: data.userId } }
  } catch (error) { return pushFailure(error) }
}

export async function revokePushDevice(request: RevokeRequest): Promise<PushResult<{ revoked: boolean }>> {
  try {
    const { operationId, operationIssuedAt, bindingId, expectedRevision } = request
    const response = await revokeApi.post<ApiResponse<{ revoked: boolean }>>(`${path(request)}/revoke`, {
      operationId, operationIssuedAt, bindingId, expectedRevision,
    }, { headers: headers(request) })
    const data = unwrapApiResponse(response.data)
    return data && typeof data.revoked === 'boolean'
      ? { outcome: 'SUCCESS', data: { revoked: data.revoked } } : invalidResponse()
  } catch (error) {
    const failure = toApiClientError(error)
    if (failure.status === 404) return { outcome: 'SUCCESS', data: { revoked: false } }
    return pushFailure(failure)
  }
}

import type { DeviceRequest, Installation, RevokeRequest } from './pushApi'

type Envelope<T extends string, P> = {
  version: 1; type: T; messageId: string; bridgeSessionId: string; authEpoch: number; payload: P
}
export type NativePushMessage =
  | Envelope<'PUSH_STATE_REQUEST', Installation & { requestId: string }>
  | Envelope<'PUSH_DEVICE', DeviceRequest>
  | Envelope<'PUSH_REVOKE', RevokeRequest>
  | Envelope<'PUSH_REGISTER_ACK', { operationId: string; bindingId: string | null; revision: number; persisted: true }>
  | Envelope<'SESSION_ENDING_ACK', { requestId: string; persisted: boolean }>

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
export const isUuid = (value: unknown): value is string => typeof value === 'string' && UUID.test(value)
const integer = (value: unknown) => Number.isSafeInteger(value) && (value as number) >= 0
const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)
const keys = (value: Record<string, unknown>, required: string[], optional: string[] = []) =>
  required.every((key) => Object.hasOwn(value, key)) && Object.keys(value).every((key) => required.includes(key) || optional.includes(key))
const installation = (value: Record<string, unknown>) => isUuid(value.installationId)
  && typeof value.installationKey === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value.installationKey)
const operation = (value: Record<string, unknown>) => isUuid(value.operationId) && integer(value.expectedRevision)
  && typeof value.operationIssuedAt === 'string'
  && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(value.operationIssuedAt)
  && Number.isFinite(Date.parse(value.operationIssuedAt))

export function parseNativePushMessage(raw: unknown, sessionId: string): NativePushMessage | null {
  if (typeof raw !== 'string' || raw.length > 8192 || new TextEncoder().encode(raw).byteLength > 8192) return null
  try {
    const value: unknown = JSON.parse(raw)
    if (!record(value) || !keys(value, ['version', 'type', 'messageId', 'bridgeSessionId', 'authEpoch', 'payload'])
      || value.version !== 1 || !isUuid(value.messageId) || value.bridgeSessionId !== sessionId
      || !integer(value.authEpoch) || !record(value.payload)) return null
    const p = value.payload
    let valid = false
    switch (value.type) {
      case 'PUSH_STATE_REQUEST':
        valid = keys(p, ['requestId', 'installationId', 'installationKey']) && isUuid(p.requestId) && installation(p)
        break
      case 'PUSH_DEVICE':
        valid = keys(p, ['installationId', 'installationKey', 'operationId', 'operationIssuedAt', 'expectedRevision', 'platform', 'permission'], ['pushToken'])
          && installation(p) && operation(p) && (p.platform === 'IOS' || p.platform === 'ANDROID')
          && (p.permission === 'GRANTED'
            ? typeof p.pushToken === 'string' && /^(?:ExponentPushToken|ExpoPushToken)\[[A-Za-z0-9_-]{1,440}\]$/.test(p.pushToken)
            : p.permission === 'DENIED' && !Object.hasOwn(p, 'pushToken'))
        break
      case 'PUSH_REVOKE':
        valid = value.authEpoch === 0 && keys(p, ['installationId', 'installationKey', 'operationId', 'operationIssuedAt', 'bindingId', 'expectedRevision'])
          && installation(p) && operation(p) && isUuid(p.bindingId)
        break
      case 'PUSH_REGISTER_ACK':
        valid = keys(p, ['operationId', 'bindingId', 'revision', 'persisted']) && isUuid(p.operationId)
          && (p.bindingId === null || isUuid(p.bindingId)) && integer(p.revision) && p.persisted === true
        break
      case 'SESSION_ENDING_ACK':
        valid = keys(p, ['requestId', 'persisted']) && isUuid(p.requestId) && typeof p.persisted === 'boolean'
    }
    return valid ? value as NativePushMessage : null
  } catch { return null }
}

export const PUSH_BRIDGE_VERSION = 1 as const;
export const PUSH_BRIDGE_CAPABILITY = 'push-v1' as const;
export const NATIVE_MESSAGE_EVENT = 'nalq:native-message' as const;
export const MAX_BRIDGE_MESSAGE_BYTES = 8 * 1024;

export type AuthPhase = 'authenticated' | 'anonymous' | 'bootstrapping';

export interface WebReadyMessage {
  version: typeof PUSH_BRIDGE_VERSION;
  type: 'WEB_READY';
  messageId: string;
  bridgeSessionId: null;
  authEpoch: 0;
  payload: {
    versions: number[];
  };
}

export interface HelloMessage {
  version: typeof PUSH_BRIDGE_VERSION;
  type: 'HELLO';
  messageId: string;
  bridgeSessionId: string;
  authEpoch: 0;
  payload: {
    version: typeof PUSH_BRIDGE_VERSION;
    bridgeSessionId: string;
    capabilities: string[];
    replyTo: string;
  };
}

export interface NativeFeatureMessage {
  version: typeof PUSH_BRIDGE_VERSION;
  type: string;
  messageId: string;
  bridgeSessionId: string;
  authEpoch: number;
  payload: unknown;
}

export interface AuthStateMessage {
  version: typeof PUSH_BRIDGE_VERSION;
  type: 'AUTH_STATE';
  messageId: string;
  bridgeSessionId: string;
  authEpoch: number;
  payload:
    | { phase: 'authenticated'; authEpoch: number; userId: number }
    | { phase: 'anonymous' | 'bootstrapping'; authEpoch: number };
}

export type AcceptedAuthState = {
  authEpoch: number;
  phase: AuthPhase;
  userId: number | null;
};

export type AuthStateDecision =
  | { accepted: true; state: AcceptedAuthState }
  | {
      accepted: false;
      reason: 'capability-not-negotiated' | 'epoch-regressed' | 'epoch-conflict';
    };

export type PushStateResultPayload = {
  requestId: string;
  outcome: 'SUCCESS';
  data: {
    revision: number;
    belongsToCurrentUser: boolean;
    bindingId: string | null;
    status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
    platform: 'IOS' | 'ANDROID';
  };
} | {
  requestId: string;
  outcome: 'NOT_FOUND';
} | {
  requestId: string;
  outcome: 'RETRY';
  errorCode: string;
  retryAfterMs?: number;
} | {
  requestId: string;
  outcome: 'FAILED';
  errorCode: string;
  retryAfterMs?: number;
};

export type PushRegisterResultPayload = {
  operationId: string;
  outcome: 'SUCCESS';
  data: {
    installationId: string;
    revision: number;
    bindingId: string | null;
    status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
    userId: number;
  };
} | {
  operationId: string;
  outcome: 'RETRY';
  errorCode: string;
  retryAfterMs?: number;
} | {
  operationId: string;
  outcome: 'FAILED';
  errorCode: string;
  retryAfterMs?: number;
};

export type PushRevokeResultPayload = {
  operationId: string;
  outcome: 'SUCCESS';
  data: { revoked: boolean };
} | {
  operationId: string;
  outcome: 'RETRY';
  errorCode: string;
  retryAfterMs?: number;
} | {
  operationId: string;
  outcome: 'FAILED';
  errorCode: string;
  retryAfterMs?: number;
};

export type WebFeatureMessage =
  | AuthStateMessage
  | (NativeFeatureMessage & { type: 'PUSH_REGISTER_REQUEST'; payload: { authEpoch: number } })
  | (NativeFeatureMessage & { type: 'PUSH_STATE_RESULT'; payload: PushStateResultPayload })
  | (NativeFeatureMessage & { type: 'PUSH_REGISTER_RESULT'; payload: PushRegisterResultPayload })
  | (NativeFeatureMessage & { type: 'SESSION_ENDING'; payload: { reason: 'LOGOUT' | 'WITHDRAWAL' } })
  | (NativeFeatureMessage & { type: 'PUSH_REVOKE_RESULT'; payload: PushRevokeResultPayload });

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]) {
  const actualKeys = Object.keys(value).sort();
  const expectedKeys = [...keys].sort();

  return actualKeys.length === expectedKeys.length
    && actualKeys.every((key, index) => key === expectedKeys[index]);
}

export function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

export function utf8ByteLength(value: string) {
  let bytes = 0;

  for (const character of value) {
    const codePoint = character.codePointAt(0) ?? 0;
    if (codePoint <= 0x7f) {
      bytes += 1;
    } else if (codePoint <= 0x7ff) {
      bytes += 2;
    } else if (codePoint <= 0xffff) {
      bytes += 3;
    } else {
      bytes += 4;
    }
  }

  return bytes;
}

function parseMessageJson(raw: string): Record<string, unknown> | null {
  if (utf8ByteLength(raw) > MAX_BRIDGE_MESSAGE_BYTES) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(raw);
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function parseWebReadyMessage(raw: string): WebReadyMessage | null {
  const value = parseMessageJson(raw);
  if (!value || !hasExactKeys(value, [
    'version',
    'type',
    'messageId',
    'bridgeSessionId',
    'authEpoch',
    'payload',
  ])) {
    return null;
  }

  const payload = value.payload;
  if (!isRecord(payload) || !hasExactKeys(payload, ['versions'])) {
    return null;
  }

  if (
    value.version !== PUSH_BRIDGE_VERSION
    || value.type !== 'WEB_READY'
    || !isUuid(value.messageId)
    || value.bridgeSessionId !== null
    || value.authEpoch !== 0
    || !Array.isArray(payload.versions)
    || payload.versions.length === 0
    || !payload.versions.every((version) => Number.isSafeInteger(version) && version > 0)
  ) {
    return null;
  }

  return value as unknown as WebReadyMessage;
}

export function createHelloMessage(
  bridgeSessionId: string,
  replyTo: string,
  messageId: string,
): HelloMessage {
  if (!isUuid(bridgeSessionId) || !isUuid(replyTo) || !isUuid(messageId)) {
    throw new Error('Bridge identifiers must be UUIDs.');
  }

  return {
    version: PUSH_BRIDGE_VERSION,
    type: 'HELLO',
    messageId,
    bridgeSessionId,
    authEpoch: 0,
    payload: {
      version: PUSH_BRIDGE_VERSION,
      bridgeSessionId,
      capabilities: [PUSH_BRIDGE_CAPABILITY],
      replyTo,
    },
  };
}

export function parseAuthStateMessage(
  raw: string,
  activeBridgeSessionId: string,
): AuthStateMessage | null {
  const value = parseMessageJson(raw);
  if (!value || !hasExactKeys(value, [
    'version',
    'type',
    'messageId',
    'bridgeSessionId',
    'authEpoch',
    'payload',
  ])) {
    return null;
  }

  if (
    value.version !== PUSH_BRIDGE_VERSION
    || value.type !== 'AUTH_STATE'
    || !isUuid(value.messageId)
    || value.bridgeSessionId !== activeBridgeSessionId
    || !Number.isSafeInteger(value.authEpoch)
    || (value.authEpoch as number) < 0
  ) {
    return null;
  }

  const payload = value.payload;
  if (
    !isRecord(payload)
    || typeof payload.phase !== 'string'
    || payload.authEpoch !== value.authEpoch
  ) {
    return null;
  }

  if (payload.phase === 'authenticated') {
    if (
      !hasExactKeys(payload, ['phase', 'authEpoch', 'userId'])
      || !Number.isSafeInteger(payload.userId)
      || (payload.userId as number) <= 0
    ) {
      return null;
    }
  } else if (
    (payload.phase !== 'anonymous' && payload.phase !== 'bootstrapping')
    || !hasExactKeys(payload, ['phase', 'authEpoch'])
  ) {
    return null;
  }

  return value as unknown as AuthStateMessage;
}

export function decideAuthState(
  current: AcceptedAuthState | null,
  incoming: AuthStateMessage,
  negotiatedCapabilities: readonly string[],
): AuthStateDecision {
  if (!negotiatedCapabilities.includes(PUSH_BRIDGE_CAPABILITY)) {
    return { accepted: false, reason: 'capability-not-negotiated' };
  }

  const next: AcceptedAuthState = {
    authEpoch: incoming.authEpoch,
    phase: incoming.payload.phase,
    userId: incoming.payload.phase === 'authenticated' ? incoming.payload.userId : null,
  };

  if (current && incoming.authEpoch < current.authEpoch) {
    return { accepted: false, reason: 'epoch-regressed' };
  }

  if (
    current
    && incoming.authEpoch === current.authEpoch
    && (current.phase !== next.phase || current.userId !== next.userId)
  ) {
    return { accepted: false, reason: 'epoch-conflict' };
  }

  return { accepted: true, state: next };
}

export function createNativeFeatureMessage(
  type: string,
  bridgeSessionId: string,
  authEpoch: number,
  payload: unknown,
  messageId: string,
): NativeFeatureMessage {
  if (
    !isUuid(bridgeSessionId)
    || !isUuid(messageId)
    || !Number.isSafeInteger(authEpoch)
    || authEpoch < 0
  ) {
    throw new Error('Native feature envelope is invalid.');
  }
  return {
    version: PUSH_BRIDGE_VERSION,
    type,
    messageId,
    bridgeSessionId,
    authEpoch,
    payload,
  };
}

export function serializeNativeMessage(message: HelloMessage | NativeFeatureMessage) {
  const serialized = JSON.stringify(message);
  if (utf8ByteLength(serialized) > MAX_BRIDGE_MESSAGE_BYTES) {
    throw new Error('Native bridge message exceeds the size limit.');
  }
  return serialized;
}

export function parseTransportMessage(raw: string, expectedDocumentNonce: string) {
  if (utf8ByteLength(raw) > MAX_BRIDGE_MESSAGE_BYTES + 512) {
    return null;
  }
  const value = parseMessageJsonWithLimit(raw, MAX_BRIDGE_MESSAGE_BYTES + 512);
  if (
    !value
    || !hasExactKeys(value, ['transportVersion', 'documentNonce', 'message'])
    || value.transportVersion !== 1
    || value.documentNonce !== expectedDocumentNonce
    || typeof value.message !== 'string'
    || utf8ByteLength(value.message) > MAX_BRIDGE_MESSAGE_BYTES
  ) {
    return null;
  }
  return value.message;
}

function parseMessageJsonWithLimit(raw: string, limit: number): Record<string, unknown> | null {
  if (utf8ByteLength(raw) > limit) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function createMainDocumentBridgeScript(webOrigin: string, documentNonce: string) {
  const expectedOrigin = JSON.stringify(webOrigin);
  const nonce = JSON.stringify(documentNonce);
  const nativeEvent = JSON.stringify(NATIVE_MESSAGE_EVENT);
  const readyEvent = JSON.stringify('nalq:native-ready');
  return `(() => {
    const expectedOrigin = ${expectedOrigin};
    const documentNonce = ${nonce};
    if (window.self !== window.top || window.location.origin !== expectedOrigin) return true;
    const nativeBridge = window.ReactNativeWebView;
    if (!nativeBridge || typeof nativeBridge.postMessage !== 'function') return true;
    const rawPostMessage = nativeBridge.postMessage.bind(nativeBridge);
    const facade = {};
    Object.defineProperty(facade, 'postMessage', { value: (message) => {
      if (typeof message !== 'string') return;
      rawPostMessage(JSON.stringify({ transportVersion: 1, documentNonce, message }));
    } });
    Object.freeze(facade);
    Object.defineProperty(window, 'NalQNativeBridge', { value: facade, configurable: true });
    Object.defineProperty(window, '__nalqDispatchNativeV1', { value: (candidateNonce, message) => {
      if (candidateNonce !== documentNonce || typeof message !== 'string') return;
      window.dispatchEvent(new CustomEvent(${nativeEvent}, { detail: message }));
    }, configurable: true });
    window.dispatchEvent(new Event(${readyEvent}));
    return true;
  })();`;
}

export function createNativeMessageDispatchScript(
  serializedMessage: string,
  webOrigin: string,
  documentNonce: string,
) {
  if (utf8ByteLength(serializedMessage) > MAX_BRIDGE_MESSAGE_BYTES) {
    throw new Error('Native bridge message exceeds the size limit.');
  }

  return `(() => {
    if (window.self !== window.top || window.location.origin !== ${JSON.stringify(webOrigin)}) return true;
    const dispatch = window.__nalqDispatchNativeV1;
    if (typeof dispatch === 'function') dispatch(${JSON.stringify(documentNonce)}, ${JSON.stringify(serializedMessage)});
    return true;
  })(); true;`;
}

function isRetryAfter(value: unknown) {
  return value === undefined || (Number.isSafeInteger(value)
    && (value as number) >= 0
    && (value as number) <= 86_400_000);
}

function isStableErrorCode(value: unknown) {
  return typeof value === 'string' && /^[A-Z][A-Z0-9_]{1,63}$/.test(value);
}

function parseResultPayload(
  payload: unknown,
  idKey: 'requestId' | 'operationId',
) {
  if (!isRecord(payload) || !isUuid(payload[idKey]) || typeof payload.outcome !== 'string') {
    return null;
  }
  if (payload.outcome === 'RETRY' || payload.outcome === 'FAILED') {
    const keys = payload.retryAfterMs === undefined
      ? [idKey, 'outcome', 'errorCode']
      : [idKey, 'outcome', 'errorCode', 'retryAfterMs'];
    if (!hasExactKeys(payload, keys) || !isStableErrorCode(payload.errorCode)
      || !isRetryAfter(payload.retryAfterMs)) {
      return null;
    }
  }
  return payload;
}

function parseDeviceState(value: unknown) {
  return isRecord(value)
    && hasExactKeys(value, [
      'revision', 'belongsToCurrentUser', 'bindingId', 'status', 'platform',
    ])
    && Number.isSafeInteger(value.revision)
    && (value.revision as number) >= 0
    && typeof value.belongsToCurrentUser === 'boolean'
    && (value.bindingId === null || isUuid(value.bindingId))
    && (value.status === 'ACTIVE' || value.status === 'DISABLED' || value.status === 'REVOKED')
    && (value.platform === 'IOS' || value.platform === 'ANDROID');
}

function parseRegistrationResult(value: unknown) {
  return isRecord(value)
    && hasExactKeys(value, [
      'installationId', 'revision', 'bindingId', 'status', 'userId',
    ])
    && isUuid(value.installationId)
    && Number.isSafeInteger(value.revision)
    && (value.revision as number) >= 0
    && (value.bindingId === null || isUuid(value.bindingId))
    && (value.status === 'ACTIVE' || value.status === 'DISABLED' || value.status === 'REVOKED')
    && Number.isSafeInteger(value.userId)
    && (value.userId as number) > 0;
}

export function parseWebFeatureMessage(
  raw: string,
  activeBridgeSessionId: string,
): WebFeatureMessage | null {
  const value = parseMessageJson(raw);
  if (!value || !hasExactKeys(value, [
    'version', 'type', 'messageId', 'bridgeSessionId', 'authEpoch', 'payload',
  ]) || value.version !== PUSH_BRIDGE_VERSION || !isUuid(value.messageId)
    || value.bridgeSessionId !== activeBridgeSessionId
    || !Number.isSafeInteger(value.authEpoch) || (value.authEpoch as number) < 0) {
    return null;
  }

  if (value.type === 'AUTH_STATE') {
    return parseAuthStateMessage(raw, activeBridgeSessionId);
  }

  const payload = value.payload;
  if (!isRecord(payload)) {
    return null;
  }

  if (value.type === 'PUSH_REGISTER_REQUEST') {
    return hasExactKeys(payload, ['authEpoch']) && payload.authEpoch === value.authEpoch
      ? value as unknown as WebFeatureMessage
      : null;
  }
  if (value.type === 'SESSION_ENDING') {
    return hasExactKeys(payload, ['reason'])
      && (payload.reason === 'LOGOUT' || payload.reason === 'WITHDRAWAL')
      ? value as unknown as WebFeatureMessage
      : null;
  }
  if (value.type === 'PUSH_STATE_RESULT') {
    const parsed = parseResultPayload(payload, 'requestId');
    if (!parsed) return null;
    if (payload.outcome === 'SUCCESS') {
      if (!hasExactKeys(payload, ['requestId', 'outcome', 'data']) || !parseDeviceState(payload.data)) return null;
    } else if (payload.outcome === 'NOT_FOUND') {
      if (!hasExactKeys(payload, ['requestId', 'outcome'])) return null;
    } else if (payload.outcome !== 'RETRY' && payload.outcome !== 'FAILED') return null;
    return value as unknown as WebFeatureMessage;
  }
  if (value.type === 'PUSH_REGISTER_RESULT') {
    const parsed = parseResultPayload(payload, 'operationId');
    if (!parsed) return null;
    if (payload.outcome === 'SUCCESS') {
      if (!hasExactKeys(payload, ['operationId', 'outcome', 'data'])
        || !parseRegistrationResult(payload.data)) return null;
    } else if (payload.outcome !== 'RETRY' && payload.outcome !== 'FAILED') return null;
    return value as unknown as WebFeatureMessage;
  }
  if (value.type === 'PUSH_REVOKE_RESULT') {
    if (value.authEpoch !== 0) return null;
    const parsed = parseResultPayload(payload, 'operationId');
    if (!parsed) return null;
    if (payload.outcome === 'SUCCESS') {
      if (!hasExactKeys(payload, ['operationId', 'outcome', 'data'])
        || !isRecord(payload.data) || !hasExactKeys(payload.data, ['revoked'])
        || typeof payload.data.revoked !== 'boolean') return null;
    } else if (payload.outcome !== 'RETRY' && payload.outcome !== 'FAILED') return null;
    return value as unknown as WebFeatureMessage;
  }
  return null;
}

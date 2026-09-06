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
      capabilities: [],
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

export function serializeNativeMessage(message: HelloMessage) {
  const serialized = JSON.stringify(message);
  if (utf8ByteLength(serialized) > MAX_BRIDGE_MESSAGE_BYTES) {
    throw new Error('Native bridge message exceeds the size limit.');
  }
  return serialized;
}

export function createNativeMessageDispatchScript(serializedMessage: string) {
  if (utf8ByteLength(serializedMessage) > MAX_BRIDGE_MESSAGE_BYTES) {
    throw new Error('Native bridge message exceeds the size limit.');
  }

  return `window.dispatchEvent(new CustomEvent(${JSON.stringify(NATIVE_MESSAGE_EVENT)}, { detail: ${JSON.stringify(serializedMessage)} })); true;`;
}

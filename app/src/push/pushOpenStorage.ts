import type { KeyValueStorage, LastRegistrationAck } from './pushStorage';

export const PUSH_OPEN_STORAGE_VERSION = 1 as const;
export const PUSH_OPEN_STORAGE_KEY = 'nalq.push.opens.v1';

export interface PendingPushOpen {
  messageId: string;
  notificationId: string;
  bindingId: string;
  sdkResponseId: string;
  capturedAt: string;
  ownerUserId: number | null;
}

interface PushOpenStorageState {
  version: typeof PUSH_OPEN_STORAGE_VERSION;
  pending: PendingPushOpen[];
  bindingOwners: BindingOwnerRecord[];
}

interface BindingOwnerRecord {
  bindingId: string;
  userId: number;
  recordedAt: string;
}

export interface PushOpenCandidate {
  notificationId: string;
  bindingId: string;
  sdkResponseId: string;
}

const OWNER_RETENTION_MS = 90 * 24 * 60 * 60 * 1_000;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]) {
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length
    && actual.every((key, index) => key === expected[index]);
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

function isIsoInstant(value: unknown): value is string {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)
    && Number.isFinite(Date.parse(value));
}

function isSdkResponseId(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 512;
}

function isPendingPushOpen(value: unknown): value is PendingPushOpen {
  return isRecord(value)
    && hasExactKeys(value, [
      'messageId',
      'notificationId',
      'bindingId',
      'sdkResponseId',
      'capturedAt',
      'ownerUserId',
    ])
    && isUuid(value.messageId)
    && isUuid(value.notificationId)
    && isUuid(value.bindingId)
    && isSdkResponseId(value.sdkResponseId)
    && isIsoInstant(value.capturedAt)
    && (value.ownerUserId === null
      || (Number.isSafeInteger(value.ownerUserId) && (value.ownerUserId as number) > 0));
}

function isBindingOwnerRecord(value: unknown): value is BindingOwnerRecord {
  return isRecord(value)
    && hasExactKeys(value, ['bindingId', 'userId', 'recordedAt'])
    && isUuid(value.bindingId)
    && Number.isSafeInteger(value.userId)
    && (value.userId as number) > 0
    && isIsoInstant(value.recordedAt);
}

function isState(value: unknown): value is PushOpenStorageState {
  return isRecord(value)
    && hasExactKeys(value, ['version', 'pending', 'bindingOwners'])
    && value.version === PUSH_OPEN_STORAGE_VERSION
    && Array.isArray(value.pending)
    && value.pending.every(isPendingPushOpen)
    && Array.isArray(value.bindingOwners)
    && value.bindingOwners.every(isBindingOwnerRecord);
}

function cloneState(state: PushOpenStorageState): PushOpenStorageState {
  return JSON.parse(JSON.stringify(state)) as PushOpenStorageState;
}

export function parsePushOpenCandidate(value: unknown): PushOpenCandidate | null {
  if (!isRecord(value) || !hasExactKeys(value, [
    'payloadVersion',
    'notificationId',
    'bindingId',
    'sdkResponseId',
  ])) {
    return null;
  }
  if (
    value.payloadVersion !== 1
    || !isUuid(value.notificationId)
    || !isUuid(value.bindingId)
    || !isSdkResponseId(value.sdkResponseId)
  ) {
    return null;
  }
  return {
    notificationId: value.notificationId,
    bindingId: value.bindingId,
    sdkResponseId: value.sdkResponseId,
  };
}

export function parseNormalizedPushOpenCandidate(value: unknown): PushOpenCandidate | null {
  if (!isRecord(value) || !hasExactKeys(value, [
    'notificationId',
    'bindingId',
    'sdkResponseId',
  ])) {
    return null;
  }
  return isUuid(value.notificationId)
    && isUuid(value.bindingId)
    && isSdkResponseId(value.sdkResponseId)
    ? value as unknown as PushOpenCandidate
    : null;
}

export function resolveRecentRegistrationAckOwner(
  bindingId: string,
  ack: LastRegistrationAck | null | undefined,
  at: string,
) {
  if (!ack?.bindingId || ack.bindingId !== bindingId) {
    return null;
  }
  const age = Date.parse(at) - Date.parse(ack.completedAt);
  return age >= 0 && age <= OWNER_RETENTION_MS ? ack.userId : null;
}

export class PushOpenStorageCorruptedError extends Error {
  constructor() {
    super('Stored push open state is invalid.');
    this.name = 'PushOpenStorageCorruptedError';
  }
}

export class PushOpenStorageRepository {
  private operationQueue: Promise<void> = Promise.resolve();

  constructor(private readonly storage: KeyValueStorage) {}

  load() {
    return this.enqueue(() => this.readState());
  }

  append(pending: PendingPushOpen) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const duplicate = current.pending.find((item) => item.sdkResponseId === pending.sdkResponseId);
      if (duplicate) {
        return { state: cloneState(current), pending: { ...duplicate }, created: false };
      }
      const next: PushOpenStorageState = {
        ...current,
        pending: [...current.pending, pending],
      };
      this.assertValid(next);
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return { state: cloneState(next), pending: { ...pending }, created: true };
    });
  }

  recordBindingOwner(bindingId: string, userId: number, recordedAt: string) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const cutoff = Date.parse(recordedAt) - OWNER_RETENTION_MS;
      const retained = current.bindingOwners.filter((item) => (
        item.bindingId !== bindingId && Date.parse(item.recordedAt) >= cutoff
      ));
      const next: PushOpenStorageState = {
        ...current,
        bindingOwners: [...retained, { bindingId, userId, recordedAt }],
      };
      this.assertValid(next);
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return cloneState(next);
    });
  }

  async findBindingOwner(bindingId: string, at: string) {
    const state = await this.load();
    const cutoff = Date.parse(at) - OWNER_RETENTION_MS;
    return state.bindingOwners.find((item) => (
      item.bindingId === bindingId && Date.parse(item.recordedAt) >= cutoff
    ))?.userId ?? null;
  }

  setOwner(messageId: string, ownerUserId: number) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const next: PushOpenStorageState = {
        ...current,
        pending: current.pending.map((item) => item.messageId === messageId
          ? { ...item, ownerUserId }
          : item),
      };
      this.assertValid(next);
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return cloneState(next);
    });
  }

  remove(messageId: string) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const next: PushOpenStorageState = {
        ...current,
        pending: current.pending.filter((item) => item.messageId !== messageId),
      };
      this.assertValid(next);
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return cloneState(next);
    });
  }

  removeForUser(userId: number) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const ownedBindingIds = new Set(current.bindingOwners
        .filter((item) => item.userId === userId)
        .map((item) => item.bindingId));
      const removed = current.pending.filter((item) => (
        item.ownerUserId === userId || ownedBindingIds.has(item.bindingId)
      ));
      const removedMessageIds = new Set(removed.map((item) => item.messageId));
      const next: PushOpenStorageState = {
        ...current,
        pending: current.pending.filter((item) => !removedMessageIds.has(item.messageId)),
        bindingOwners: current.bindingOwners.filter((item) => item.userId !== userId),
      };
      this.assertValid(next);
      if (removed.length === 0 && next.bindingOwners.length === current.bindingOwners.length) {
        return { state: cloneState(current), removed: [] };
      }
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return { state: cloneState(next), removed: removed.map((item) => ({ ...item })) };
    });
  }

  removeExpired(at: string) {
    return this.enqueue(async () => {
      const current = await this.readState();
      const cutoff = Date.parse(at) - OWNER_RETENTION_MS;
      const removed = current.pending.filter((item) => Date.parse(item.capturedAt) < cutoff);
      const removedMessageIds = new Set(removed.map((item) => item.messageId));
      const next: PushOpenStorageState = {
        ...current,
        pending: current.pending.filter((item) => !removedMessageIds.has(item.messageId)),
        bindingOwners: current.bindingOwners.filter(
          (item) => Date.parse(item.recordedAt) >= cutoff,
        ),
      };
      this.assertValid(next);
      if (removed.length === 0 && next.bindingOwners.length === current.bindingOwners.length) {
        return { state: cloneState(current), removed: [] };
      }
      await this.storage.setItem(PUSH_OPEN_STORAGE_KEY, JSON.stringify(next));
      return { state: cloneState(next), removed: removed.map((item) => ({ ...item })) };
    });
  }

  private async readState(): Promise<PushOpenStorageState> {
    const raw = await this.storage.getItem(PUSH_OPEN_STORAGE_KEY);
    if (raw === null) {
      return { version: PUSH_OPEN_STORAGE_VERSION, pending: [], bindingOwners: [] };
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (!isState(parsed)) {
        throw new PushOpenStorageCorruptedError();
      }
      return parsed;
    } catch (error) {
      if (error instanceof PushOpenStorageCorruptedError) {
        throw error;
      }
      throw new PushOpenStorageCorruptedError();
    }
  }

  private assertValid(state: PushOpenStorageState) {
    if (!isState(state)) {
      throw new PushOpenStorageCorruptedError();
    }
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.operationQueue.then(operation, operation);
    this.operationQueue = result.then(() => undefined, () => undefined);
    return result;
  }
}

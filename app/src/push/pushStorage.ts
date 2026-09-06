export const PUSH_STORAGE_VERSION = 1 as const;
export const PUSH_STORAGE_KEY = 'nalq.push.state.v1';

export interface KeyValueStorage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
}

export interface InstallationCredentials {
  installationId: string;
  installationKey: string;
  createdAt: string;
  tokenVersion: number;
}

export interface ActivePushBinding {
  bindingId: string;
  userId: number;
  revision: number;
}

export interface PendingRegistration {
  operationId: string;
  operationIssuedAt: string;
  authEpoch: number;
  userId: number;
  expectedRevision: number;
  tokenVersion: number;
  platform: 'IOS' | 'ANDROID';
  permission: 'GRANTED' | 'DENIED';
  pushToken: string | null;
}

export interface PendingRevoke {
  operationId: string;
  operationIssuedAt: string;
  bindingId: string;
  expectedRevision: number;
}

export interface PushStorageState {
  version: typeof PUSH_STORAGE_VERSION;
  installation: InstallationCredentials;
  activeBinding: ActivePushBinding | null;
  pendingRegistration: PendingRegistration | null;
  pendingRevokes: PendingRevoke[];
}

export class PushStorageCorruptedError extends Error {
  constructor() {
    super('Stored push state is invalid.');
    this.name = 'PushStorageCorruptedError';
  }
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const INSTALLATION_KEY_PATTERN = /^[A-Za-z0-9_-]{43}$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isUuid(value: unknown): value is string {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

function isIsoInstant(value: unknown): value is string {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)
    && Number.isFinite(Date.parse(value));
}

function isNonNegativeInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0;
}

function isPositiveInteger(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) > 0;
}

function isInstallation(value: unknown): value is InstallationCredentials {
  return isRecord(value)
    && isUuid(value.installationId)
    && typeof value.installationKey === 'string'
    && INSTALLATION_KEY_PATTERN.test(value.installationKey)
    && isIsoInstant(value.createdAt)
    && isNonNegativeInteger(value.tokenVersion);
}

function isActiveBinding(value: unknown): value is ActivePushBinding {
  return isRecord(value)
    && isUuid(value.bindingId)
    && isPositiveInteger(value.userId)
    && isNonNegativeInteger(value.revision);
}

function isPendingRegistration(value: unknown): value is PendingRegistration {
  return isRecord(value)
    && isUuid(value.operationId)
    && isIsoInstant(value.operationIssuedAt)
    && isNonNegativeInteger(value.authEpoch)
    && isPositiveInteger(value.userId)
    && isNonNegativeInteger(value.expectedRevision)
    && isNonNegativeInteger(value.tokenVersion)
    && (value.platform === 'IOS' || value.platform === 'ANDROID')
    && (value.permission === 'GRANTED' || value.permission === 'DENIED')
    && (value.pushToken === null || isNonEmptyString(value.pushToken))
    && (value.permission === 'GRANTED' ? isNonEmptyString(value.pushToken) : value.pushToken === null);
}

function isPendingRevoke(value: unknown): value is PendingRevoke {
  return isRecord(value)
    && isUuid(value.operationId)
    && isIsoInstant(value.operationIssuedAt)
    && isUuid(value.bindingId)
    && isNonNegativeInteger(value.expectedRevision);
}

export function isPushStorageState(value: unknown): value is PushStorageState {
  return isRecord(value)
    && value.version === PUSH_STORAGE_VERSION
    && isInstallation(value.installation)
    && (value.activeBinding === null || isActiveBinding(value.activeBinding))
    && (value.pendingRegistration === null || isPendingRegistration(value.pendingRegistration))
    && Array.isArray(value.pendingRevokes)
    && value.pendingRevokes.every(isPendingRevoke);
}

function cloneState(state: PushStorageState): PushStorageState {
  return JSON.parse(JSON.stringify(state)) as PushStorageState;
}

export class PushStorageRepository {
  private operationQueue: Promise<void> = Promise.resolve();

  constructor(private readonly storage: KeyValueStorage) {}

  load() {
    return this.enqueue(() => this.readState());
  }

  getOrCreateInstallation(
    createInstallation: () => Promise<InstallationCredentials>,
  ) {
    return this.enqueue(async () => {
      const existing = await this.readState();
      if (existing) {
        return cloneState(existing);
      }

      const installation = await createInstallation();
      const state: PushStorageState = {
        version: PUSH_STORAGE_VERSION,
        installation,
        activeBinding: null,
        pendingRegistration: null,
        pendingRevokes: [],
      };
      this.assertValid(state);
      await this.storage.setItem(PUSH_STORAGE_KEY, JSON.stringify(state));
      return cloneState(state);
    });
  }

  update(mutate: (current: PushStorageState) => PushStorageState) {
    return this.enqueue(async () => {
      const current = await this.readState();
      if (!current) {
        throw new PushStorageCorruptedError();
      }

      const next = mutate(cloneState(current));
      this.assertValid(next);
      await this.storage.setItem(PUSH_STORAGE_KEY, JSON.stringify(next));
      return cloneState(next);
    });
  }

  private async readState() {
    const raw = await this.storage.getItem(PUSH_STORAGE_KEY);
    if (raw === null) {
      return null;
    }

    try {
      const value: unknown = JSON.parse(raw);
      if (!isPushStorageState(value)) {
        throw new PushStorageCorruptedError();
      }
      return value;
    } catch (error) {
      if (error instanceof PushStorageCorruptedError) {
        throw error;
      }
      throw new PushStorageCorruptedError();
    }
  }

  private assertValid(state: PushStorageState) {
    if (!isPushStorageState(state)) {
      throw new PushStorageCorruptedError();
    }
  }

  private enqueue<T>(operation: () => Promise<T>): Promise<T> {
    const result = this.operationQueue.then(operation, operation);
    this.operationQueue = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }
}

export function encodeBase64Url(bytes: Uint8Array) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  let result = '';

  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index] ?? 0;
    const second = bytes[index + 1];
    const third = bytes[index + 2];
    const chunk = (first << 16) | ((second ?? 0) << 8) | (third ?? 0);

    result += alphabet[(chunk >> 18) & 0x3f];
    result += alphabet[(chunk >> 12) & 0x3f];
    if (second !== undefined) {
      result += alphabet[(chunk >> 6) & 0x3f];
    }
    if (third !== undefined) {
      result += alphabet[chunk & 0x3f];
    }
  }

  return result;
}

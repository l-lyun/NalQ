import type {
  AcceptedAuthState,
  PushRegisterResultPayload,
  PushRevokeResultPayload,
  PushStateResultPayload,
} from './bridgeProtocol';
import type {
  InstallationCredentials,
  PendingRegistration,
  PendingRevoke,
  PushStorageRepository,
} from './pushStorage';

export interface PushRegistrationTarget {
  platform: 'IOS' | 'ANDROID';
  permission: 'GRANTED' | 'DENIED';
  pushToken: string | null;
}

export interface PushRegistrationProvider {
  resolve(): Promise<PushRegistrationTarget>;
}

export type NativePushMessageSender = (
  type: string,
  payload: unknown,
  authEpoch: number,
) => string | null;

interface CoordinatorDependencies {
  storage: PushStorageRepository;
  createInstallation: () => Promise<InstallationCredentials>;
  registrationProvider: PushRegistrationProvider;
  createMessageId: () => string;
  now: () => string;
  schedule: (callback: () => void, delayMs: number) => unknown;
  cancelSchedule: (handle: unknown) => void;
}

interface StateRequestContext {
  requestId: string;
  authEpoch: number;
  userId: number;
  target: PushRegistrationTarget;
  rotateIfMissing: boolean;
  generation: number;
  installationId: string;
  installationKey: string;
}

const MAX_IN_SESSION_RETRIES = 3;
const MIN_RETRY_MS = 500;
const MAX_BACKOFF_RETRY_MS = 30_000;
const RESPONSE_TIMEOUT_BASE_MS = 12_000;

function retryDelay(value: number | undefined, attempt: number) {
  if (value !== undefined && Number.isFinite(value)) {
    // Retry-After는 서버가 요구한 최소 대기이므로 임의로 줄이지 않는다.
    return Math.max(MIN_RETRY_MS, Math.trunc(value));
  }
  return Math.min(
    MAX_BACKOFF_RETRY_MS,
    RESPONSE_TIMEOUT_BASE_MS * (2 ** Math.max(0, attempt - 1)),
  );
}

function sameRegistration(
  pending: PendingRegistration,
  auth: AcceptedAuthState,
  target: PushRegistrationTarget,
) {
  return pending.authEpoch === auth.authEpoch
    && pending.userId === auth.userId
    && pending.platform === target.platform
    && pending.permission === target.permission
    && pending.pushToken === target.pushToken;
}

export class PushRegistrationCoordinator {
  private authState: AcceptedAuthState | null = null;
  private lastAuthenticatedState: AcceptedAuthState | null = null;
  private sessionId: string | null = null;
  private sender: NativePushMessageSender | null = null;
  private stateRequest: StateRequestContext | null = null;
  private registrationGeneration = 0;
  private registrationQueue: Promise<void> = Promise.resolve();
  private retryAttempts = new Map<string, number>();
  private scheduledRetries = new Map<string, unknown>();

  constructor(private readonly dependencies: CoordinatorDependencies) {}

  connect(sessionId: string, sender: NativePushMessageSender) {
    this.disconnect();
    this.sessionId = sessionId;
    this.sender = sender;
  }

  disconnect() {
    this.registrationGeneration += 1;
    this.authState = null;
    this.lastAuthenticatedState = null;
    this.sessionId = null;
    this.sender = null;
    this.stateRequest = null;
    for (const handle of this.scheduledRetries.values()) {
      this.dependencies.cancelSchedule(handle);
    }
    this.scheduledRetries.clear();
    this.retryAttempts.clear();
  }

  async acceptAuthState(next: AcceptedAuthState) {
    const previous = this.authState;
    this.authState = next;
    if (next.phase === 'authenticated') {
      this.lastAuthenticatedState = next;
    }

    if (
      previous
      && (previous.authEpoch !== next.authEpoch || previous.userId !== next.userId)
    ) {
      this.registrationGeneration += 1;
      this.stateRequest = null;
    }

    if (next.phase !== 'authenticated') {
      this.registrationGeneration += 1;
      this.stateRequest = null;
    }
  }

  requestRegistration(authEpoch: number) {
    const requestedGeneration = this.registrationGeneration;
    const work = async () => {
      await this.startRegistration(authEpoch, requestedGeneration);
    };
    const result = this.registrationQueue.then(work, work);
    this.registrationQueue = result.then(() => undefined, () => undefined);
    return result;
  }

  refreshRegistration() {
    const auth = this.authState;
    if (auth?.phase !== 'authenticated') {
      return Promise.resolve();
    }
    return this.requestRegistration(auth.authEpoch);
  }

  private async startRegistration(authEpoch: number, generation: number) {
    const auth = this.authState;
    if (
      !auth
      || auth.phase !== 'authenticated'
      || auth.authEpoch !== authEpoch
      || auth.userId === null
      || generation !== this.registrationGeneration
      || !this.sender
    ) {
      return;
    }

    let target: PushRegistrationTarget;
    try {
      target = await this.dependencies.registrationProvider.resolve();
      this.clearRetry(`provider:${auth.userId}:${authEpoch}`);
    } catch {
      if (this.matchesCurrentAuth(auth.userId, authEpoch, generation)) {
        this.scheduleRetry(`provider:${auth.userId}:${authEpoch}`, undefined, () => {
          void this.requestRegistration(authEpoch);
        });
      }
      return;
    }
    if (!this.matchesCurrentAuth(auth.userId, authEpoch, generation)) {
      return;
    }

    const state = await this.dependencies.storage.getOrCreateInstallation(
      this.dependencies.createInstallation,
    );
    if (!this.matchesCurrentAuth(auth.userId, authEpoch, generation)) {
      return;
    }

    if (state.pendingRegistration && sameRegistration(state.pendingRegistration, auth, target)) {
      this.sendPendingRegistration(state.installation, state.pendingRegistration);
      return;
    }

    const rotateIfMissing = state.activeBinding !== null
      || state.pendingRegistration !== null
      || (state.lastRegistrationAck?.revision ?? 0) > 0;
    if (state.pendingRegistration !== null) {
      await this.dependencies.storage.update((current) => ({
        ...current,
        pendingRegistration: null,
      }));
    }

    const requestId = this.dependencies.createMessageId();
    this.stateRequest = {
      requestId,
      authEpoch,
      userId: auth.userId,
      target,
      rotateIfMissing,
      generation,
      installationId: state.installation.installationId,
      installationKey: state.installation.installationKey,
    };
    this.sendStateRequest(this.stateRequest);
  }

  async acceptStateResult(message: {
    authEpoch: number;
    payload: PushStateResultPayload;
  }) {
    const context = this.stateRequest;
    if (
      !context
      || context.requestId !== message.payload.requestId
      || context.authEpoch !== message.authEpoch
      || !this.matchesCurrentAuth(context.userId, context.authEpoch, context.generation)
    ) {
      return;
    }

    if (message.payload.outcome === 'RETRY') {
      this.clearScheduledRetry(`state:${context.requestId}`);
      this.scheduleRetry(`state:${context.requestId}`, message.payload.retryAfterMs, () => {
        if (this.stateRequest === context) {
          this.sendStateRequest(context);
        }
      });
      return;
    }

    if (message.payload.outcome === 'FAILED') {
      this.clearRetry(`state:${context.requestId}`);
      this.stateRequest = null;
      return;
    }

    this.clearRetry(`state:${context.requestId}`);
    this.stateRequest = null;
    let state = await this.dependencies.storage.load();
    if (!state || !this.matchesCurrentAuth(context.userId, context.authEpoch, context.generation)) {
      return;
    }

    let expectedRevision = 0;
    if (message.payload.outcome === 'SUCCESS') {
      expectedRevision = message.payload.data.revision;
    } else if (context.rotateIfMissing) {
      const replacement = await this.dependencies.createInstallation();
      state = await this.dependencies.storage.update((current) => ({
        ...current,
        installation: replacement,
        activeBinding: null,
        pendingRegistration: null,
      }));
    }

    const previousToken = state.activeBinding?.pushToken ?? null;
    const tokenChanged = context.target.permission === 'GRANTED'
      && context.target.pushToken !== previousToken;
    const tokenVersion = tokenChanged
      ? state.installation.tokenVersion + 1
      : state.installation.tokenVersion;
    const pending: PendingRegistration = {
      operationId: this.dependencies.createMessageId(),
      operationIssuedAt: this.dependencies.now(),
      authEpoch: context.authEpoch,
      userId: context.userId,
      expectedRevision,
      tokenVersion,
      platform: context.target.platform,
      permission: context.target.permission,
      pushToken: context.target.pushToken,
    };

    state = await this.dependencies.storage.update((current) => ({
      ...current,
      installation: { ...current.installation, tokenVersion },
      pendingRegistration: pending,
    }));
    if (!this.matchesCurrentAuth(context.userId, context.authEpoch, context.generation)) {
      return;
    }
    this.sendPendingRegistration(state.installation, pending);
  }

  async acceptRegistrationResult(message: {
    authEpoch: number;
    payload: PushRegisterResultPayload;
  }) {
    const resultGeneration = this.registrationGeneration;
    const state = await this.dependencies.storage.load();
    const pending = state?.pendingRegistration;
    if (!pending && state?.lastRegistrationAck
      && message.payload.outcome === 'SUCCESS'
      && state.lastRegistrationAck.operationId === message.payload.operationId
      && state.lastRegistrationAck.installationId === message.payload.data.installationId
      && state.lastRegistrationAck.userId === message.payload.data.userId
      && state.lastRegistrationAck.bindingId === message.payload.data.bindingId
      && state.lastRegistrationAck.revision === message.payload.data.revision
      && state.lastRegistrationAck.authEpoch === message.authEpoch
      && this.matchesCurrentAuth(
        state.lastRegistrationAck.userId,
        state.lastRegistrationAck.authEpoch,
        resultGeneration,
      )) {
      this.sendRegistrationAck(state.lastRegistrationAck);
      return;
    }
    if (
      !state
      || !pending
      || pending.operationId !== message.payload.operationId
      || pending.authEpoch !== message.authEpoch
      || !this.matchesCurrentAuth(pending.userId, pending.authEpoch, resultGeneration)
    ) {
      return;
    }

    if (message.payload.outcome === 'RETRY') {
      this.clearScheduledRetry(`register:${pending.operationId}`);
      this.scheduleRetry(`register:${pending.operationId}`, message.payload.retryAfterMs, () => {
        void this.resumePendingRegistration(pending.operationId);
      });
      return;
    }

    if (message.payload.outcome === 'FAILED') {
      this.clearRetry(`register:${pending.operationId}`);
      await this.dependencies.storage.update((current) => current.pendingRegistration?.operationId
        === pending.operationId
        ? { ...current, pendingRegistration: null }
        : current);
      if (
        message.payload.errorCode === 'PUSH_REVISION_CONFLICT'
        || message.payload.errorCode === 'PUSH_OPERATION_EXPIRED'
        || message.payload.errorCode === 'PUSH_OPERATION_CONFLICT'
      ) {
        this.scheduleRetry(`reconcile:${pending.operationId}`, message.payload.retryAfterMs, () => {
          void this.requestRegistration(pending.authEpoch);
        });
      }
      return;
    }

    const result = message.payload.data;
    if (
      result.installationId !== state.installation.installationId
      || result.userId !== pending.userId
      || result.revision < pending.expectedRevision
      || (result.status === 'ACTIVE' && result.bindingId === null)
      || (result.status === 'ACTIVE'
        && (pending.permission !== 'GRANTED' || pending.pushToken === null))
    ) {
      return;
    }

    await this.dependencies.storage.update((current) => {
      if (
        current.pendingRegistration?.operationId !== pending.operationId
        || !this.matchesCurrentAuth(
          pending.userId,
          pending.authEpoch,
          resultGeneration,
        )
      ) {
        return current;
      }
      return {
        ...current,
        activeBinding: result.status === 'ACTIVE' && result.bindingId
          ? {
              bindingId: result.bindingId,
              userId: pending.userId,
              revision: result.revision,
              platform: pending.platform,
              permission: 'GRANTED',
              pushToken: pending.pushToken as string,
              tokenVersion: pending.tokenVersion,
            }
          : null,
        pendingRegistration: null,
        lastRegistrationAck: {
          operationId: pending.operationId,
          installationId: result.installationId,
          userId: pending.userId,
          authEpoch: pending.authEpoch,
          bindingId: result.bindingId,
          revision: result.revision,
          completedAt: this.dependencies.now(),
        },
      };
    });

    if (!this.matchesCurrentAuth(pending.userId, pending.authEpoch, resultGeneration)) {
      return;
    }
    this.clearRetry(`register:${pending.operationId}`);
    this.sendRegistrationAck({
      operationId: pending.operationId,
      authEpoch: pending.authEpoch,
      bindingId: result.bindingId,
      revision: result.revision,
    });
  }

  async captureSessionEnding(message: {
    messageId: string;
    authEpoch: number;
    payload: { reason: 'LOGOUT' | 'WITHDRAWAL' };
  }) {
    const authenticated = this.lastAuthenticatedState;
    if (
      !authenticated
      || authenticated.phase !== 'authenticated'
      || authenticated.userId === null
      || authenticated.authEpoch !== message.authEpoch
    ) {
      return;
    }

    this.registrationGeneration += 1;
    this.stateRequest = null;
    const updated = await this.dependencies.storage.getOrCreateInstallation(
      this.dependencies.createInstallation,
    ).then(() => this.dependencies.storage.update((current) => {
      const binding = current.activeBinding;
      const alreadyCaptured = binding
        ? current.pendingRevokes.some((item) => item.bindingId === binding.bindingId)
        : false;
      const revoke: PendingRevoke | null = binding && binding.userId === authenticated.userId
        && !alreadyCaptured
        ? {
            installationId: current.installation.installationId,
            installationKey: current.installation.installationKey,
            operationId: this.dependencies.createMessageId(),
            operationIssuedAt: this.dependencies.now(),
            bindingId: binding.bindingId,
            expectedRevision: binding.revision,
          }
        : null;
      return {
        ...current,
        activeBinding: binding?.userId === authenticated.userId ? null : binding,
        // 응답이 유실된 최초 등록은 bindingId를 알 수 없다. 서버 logout 경합을
        // 후속 인증 상태 조회로 조정할 수 있도록 의도를 지우지 않는다.
        pendingRegistration: current.pendingRegistration,
        pendingRevokes: revoke ? [...current.pendingRevokes, revoke] : current.pendingRevokes,
      };
    }));

    this.send('SESSION_ENDING_ACK', {
      requestId: message.messageId,
      persisted: true,
    }, message.authEpoch);
    await this.flushPendingRevokesFromState(updated.pendingRevokes);
  }

  async flushPendingRevokes() {
    const state = await this.dependencies.storage.load();
    if (state) {
      await this.flushPendingRevokesFromState(state.pendingRevokes);
    }
  }

  async acceptRevokeResult(message: { payload: PushRevokeResultPayload }) {
    const state = await this.dependencies.storage.load();
    const pending = state?.pendingRevokes.find(
      (item) => item.operationId === message.payload.operationId,
    );
    if (!state || !pending) {
      return;
    }

    if (message.payload.outcome === 'SUCCESS') {
      await this.dependencies.storage.update((current) => ({
        ...current,
        pendingRevokes: current.pendingRevokes.filter(
          (item) => item.operationId !== pending.operationId,
        ),
      }));
      this.clearRetry(`revoke:${pending.operationId}`);
      return;
    }

    if (message.payload.outcome === 'RETRY') {
      this.clearScheduledRetry(`revoke:${pending.operationId}`);
      this.scheduleRetry(`revoke:${pending.operationId}`, message.payload.retryAfterMs, () => {
        this.sendPendingRevoke(pending);
      });
      return;
    }

    if (
      message.payload.errorCode === 'PUSH_OPERATION_EXPIRED'
      || message.payload.errorCode === 'PUSH_OPERATION_CONFLICT'
    ) {
      this.clearRetry(`revoke:${pending.operationId}`);
      const replacement = {
        ...pending,
        operationId: this.dependencies.createMessageId(),
        operationIssuedAt: this.dependencies.now(),
      };
      await this.dependencies.storage.update((current) => ({
        ...current,
        pendingRevokes: current.pendingRevokes.map((item) => item.operationId === pending.operationId
          ? replacement
          : item),
      }));
      this.sendPendingRevoke(replacement);
      return;
    }
    this.clearRetry(`revoke:${pending.operationId}`);
  }

  private async resumePendingRegistration(operationId: string) {
    const state = await this.dependencies.storage.load();
    const pending = state?.pendingRegistration;
    if (
      state
      && pending?.operationId === operationId
      && this.matchesCurrentAuth(pending.userId, pending.authEpoch, this.registrationGeneration)
    ) {
      this.sendPendingRegistration(state.installation, pending);
    }
  }

  private async flushPendingRevokesFromState(revokes: PendingRevoke[]) {
    for (const revoke of revokes) {
      this.sendPendingRevoke(revoke);
    }
  }

  private sendPendingRegistration(
    installation: InstallationCredentials,
    pending: PendingRegistration,
  ) {
    this.send('PUSH_DEVICE', {
      installationId: installation.installationId,
      installationKey: installation.installationKey,
      operationId: pending.operationId,
      operationIssuedAt: pending.operationIssuedAt,
      expectedRevision: pending.expectedRevision,
      platform: pending.platform,
      permission: pending.permission,
      ...(pending.permission === 'GRANTED' ? { pushToken: pending.pushToken } : {}),
    }, pending.authEpoch);
    this.scheduleRetry(`register:${pending.operationId}`, undefined, () => {
      void this.resumePendingRegistration(pending.operationId);
    });
  }

  private sendPendingRevoke(pending: PendingRevoke) {
    this.send('PUSH_REVOKE', {
      installationId: pending.installationId,
      installationKey: pending.installationKey,
      operationId: pending.operationId,
      operationIssuedAt: pending.operationIssuedAt,
      bindingId: pending.bindingId,
      expectedRevision: pending.expectedRevision,
    }, 0);
    this.scheduleRetry(`revoke:${pending.operationId}`, undefined, () => {
      this.sendPendingRevoke(pending);
    });
  }

  private sendStateRequest(context: StateRequestContext) {
    if (this.stateRequest !== context
      || !this.matchesCurrentAuth(context.userId, context.authEpoch, context.generation)) {
      return;
    }
    this.send('PUSH_STATE_REQUEST', {
      requestId: context.requestId,
      installationId: context.installationId,
      installationKey: context.installationKey,
    }, context.authEpoch);
    this.scheduleRetry(`state:${context.requestId}`, undefined, () => {
      this.sendStateRequest(context);
    });
  }

  private sendRegistrationAck(ack: {
    operationId: string;
    authEpoch: number;
    bindingId: string | null;
    revision: number;
  }) {
    this.send('PUSH_REGISTER_ACK', {
      operationId: ack.operationId,
      bindingId: ack.bindingId,
      revision: ack.revision,
      persisted: true,
    }, ack.authEpoch);
  }

  private send(type: string, payload: unknown, authEpoch: number) {
    return this.sender?.(type, payload, authEpoch) ?? null;
  }

  private matchesCurrentAuth(userId: number, authEpoch: number, generation: number) {
    return generation === this.registrationGeneration
      && this.authState?.phase === 'authenticated'
      && this.authState.authEpoch === authEpoch
      && this.authState.userId === userId;
  }

  private scheduleRetry(key: string, requestedDelay: number | undefined, callback: () => void) {
    if (this.scheduledRetries.has(key)) {
      return;
    }
    const attempt = (this.retryAttempts.get(key) ?? 0) + 1;
    if (attempt > MAX_IN_SESSION_RETRIES) {
      return;
    }
    this.retryAttempts.set(key, attempt);
    const handle = this.dependencies.schedule(() => {
      this.scheduledRetries.delete(key);
      callback();
    }, retryDelay(requestedDelay, attempt));
    this.scheduledRetries.set(key, handle);
  }

  private clearRetry(key: string) {
    const handle = this.scheduledRetries.get(key);
    if (handle !== undefined) {
      this.dependencies.cancelSchedule(handle);
    }
    this.scheduledRetries.delete(key);
    this.retryAttempts.delete(key);
  }

  private clearScheduledRetry(key: string) {
    const handle = this.scheduledRetries.get(key);
    if (handle !== undefined) {
      this.dependencies.cancelSchedule(handle);
    }
    this.scheduledRetries.delete(key);
  }
}

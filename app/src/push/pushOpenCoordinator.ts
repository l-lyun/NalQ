import type { AcceptedAuthState, PushOpenAckPayload } from './bridgeProtocol';
import {
  parseNormalizedPushOpenCandidate,
  type PendingPushOpen,
  type PushOpenCandidate,
  type PushOpenStorageRepository,
} from './pushOpenStorage';

type PushOpenSender = (
  pending: PendingPushOpen,
  authEpoch: number,
) => string | null;

interface PushOpenCoordinatorDependencies {
  storage: PushOpenStorageRepository;
  createMessageId: () => string;
  now: () => string;
  resolveBindingOwner: (bindingId: string) => Promise<number | null>;
  clearLastResponseIfMatches: (sdkResponseId: string) => void;
}

export class PushOpenCoordinator {
  private sender: PushOpenSender | null = null;
  private authState: AcceptedAuthState | null = null;
  private delivered = new Set<string>();
  private connectionGeneration = 0;

  constructor(private readonly dependencies: PushOpenCoordinatorDependencies) {}

  connect(sender: PushOpenSender) {
    this.connectionGeneration += 1;
    this.sender = sender;
    this.authState = null;
    this.delivered.clear();
    return this.flush();
  }

  disconnect() {
    this.connectionGeneration += 1;
    this.sender = null;
    this.authState = null;
    this.delivered.clear();
  }

  async capture(rawCandidate: unknown) {
    const candidate = parseNormalizedPushOpenCandidate(rawCandidate);
    if (!candidate) {
      return false;
    }
    const ownerUserId = await this.dependencies.resolveBindingOwner(candidate.bindingId);
    const result = await this.dependencies.storage.append({
      ...candidate,
      messageId: this.dependencies.createMessageId(),
      capturedAt: this.dependencies.now(),
      ownerUserId,
    });
    await this.deliver([result.pending]);
    return true;
  }

  async acceptAuthState(authState: AcceptedAuthState) {
    this.authState = authState;
    await this.flush();
  }

  async acceptAck(
    ack: PushOpenAckPayload,
    envelopeAuthEpoch: number,
  ) {
    const authenticated = this.authState;
    if (
      authenticated?.phase !== 'authenticated'
      || authenticated.userId !== ack.userId
      || authenticated.authEpoch !== envelopeAuthEpoch
    ) {
      return false;
    }

    const state = await this.dependencies.storage.load();
    if (this.authState !== authenticated) {
      return false;
    }
    let pending = state.pending.find((item) => item.messageId === ack.messageId);
    if (!pending) {
      return false;
    }

    if (pending.ownerUserId === null) {
      const resolvedOwner = await this.dependencies.resolveBindingOwner(pending.bindingId);
      if (resolvedOwner === null) {
        return false;
      }
      const updated = await this.dependencies.storage.setOwner(pending.messageId, resolvedOwner);
      if (this.authState !== authenticated) {
        return false;
      }
      pending = updated.pending.find((item) => item.messageId === ack.messageId) ?? pending;
    }
    if (pending.ownerUserId !== ack.userId) {
      return false;
    }

    this.dependencies.clearLastResponseIfMatches(pending.sdkResponseId);
    await this.dependencies.storage.remove(pending.messageId);
    return true;
  }

  async handleWithdrawal(userId: number) {
    const current = await this.dependencies.storage.load();
    const ownedBindingIds = new Set(current.bindingOwners
      .filter((item) => item.userId === userId)
      .map((item) => item.bindingId));
    this.clearMatchingSdkResponses(current.pending.filter((item) => (
      item.ownerUserId === userId || ownedBindingIds.has(item.bindingId)
    )));
    const result = await this.dependencies.storage.removeForUser(userId);
    this.clearMatchingSdkResponses(result.removed);
    for (const pending of result.removed) {
      for (const deliveryKey of this.delivered) {
        if (deliveryKey.startsWith(`${pending.messageId}:`)) {
          this.delivered.delete(deliveryKey);
        }
      }
    }
  }

  async flush() {
    const at = this.dependencies.now();
    const current = await this.dependencies.storage.load();
    const cutoff = Date.parse(at) - 90 * 24 * 60 * 60 * 1_000;
    this.clearMatchingSdkResponses(
      current.pending.filter((item) => Date.parse(item.capturedAt) < cutoff),
    );
    const result = await this.dependencies.storage.removeExpired(at);
    this.clearMatchingSdkResponses(result.removed);
    await this.deliver(result.state.pending);
  }

  private clearMatchingSdkResponses(pendingItems: PendingPushOpen[]) {
    for (const pending of pendingItems) {
      this.dependencies.clearLastResponseIfMatches(pending.sdkResponseId);
    }
  }

  private async deliver(pendingItems: PendingPushOpen[]) {
    const sender = this.sender;
    const generation = this.connectionGeneration;
    if (!sender) {
      return;
    }
    for (let pending of pendingItems) {
      let context = this.deliveryContext(pending);
      if (context === null) {
        continue;
      }
      if (context.phase === 'authenticated' && pending.ownerUserId === null) {
        const ownerUserId = await this.dependencies.resolveBindingOwner(pending.bindingId);
        if (ownerUserId === null) {
          continue;
        }
        const state = await this.dependencies.storage.setOwner(pending.messageId, ownerUserId);
        pending = state.pending.find((item) => item.messageId === pending.messageId) ?? pending;
        context = this.deliveryContext(pending);
        if (context === null) {
          continue;
        }
      }

      if (generation !== this.connectionGeneration || sender !== this.sender) {
        return;
      }

      const deliveryKey = `${pending.messageId}:${context.phase}:${context.authEpoch}`;
      if (this.delivered.has(deliveryKey)) {
        continue;
      }
      const deliveredMessageId = sender(pending, context.authEpoch);
      if (deliveredMessageId === pending.messageId) {
        this.delivered.add(deliveryKey);
      }
    }
  }

  private deliveryContext(pending: PendingPushOpen) {
    if (!this.authState || this.authState.phase !== 'authenticated') {
      return { phase: 'anonymous' as const, authEpoch: 0 };
    }
    return pending.ownerUserId === null || pending.ownerUserId === this.authState.userId
      ? { phase: 'authenticated' as const, authEpoch: this.authState.authEpoch }
      : null;
  }
}

export type { PushOpenCandidate };

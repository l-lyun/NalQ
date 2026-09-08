const fs = require('node:fs');
const assert = require('node:assert/strict');
const test = require('node:test');
const ts = require('typescript');

require.extensions['.ts'] = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: filename,
  }).outputText;
  module._compile(output, filename);
};

const { PushOpenCoordinator } = require('../src/push/pushOpenCoordinator.ts');
const { parseWebFeatureMessage } = require('../src/push/bridgeProtocol.ts');
const {
  PUSH_OPEN_STORAGE_KEY,
  PushOpenStorageRepository,
  parsePushOpenCandidate,
  resolveRecentRegistrationAckOwner,
} = require('../src/push/pushOpenStorage.ts');

const MESSAGE_ID = '11111111-1111-4111-8111-111111111111';
const NOTIFICATION_ID = '22222222-2222-4222-8222-222222222222';
const BINDING_ID = '33333333-3333-4333-8333-333333333333';
const USER_ID = 42;

class MemoryStorage {
  constructor() {
    this.values = new Map();
    this.failNextWrite = false;
  }

  async getItem(key) { return this.values.get(key) ?? null; }
  async setItem(key, value) {
    if (this.failNextWrite) {
      this.failNextWrite = false;
      throw new Error('simulated durable failure');
    }
    this.values.set(key, value);
  }
}

function candidate(overrides = {}) {
  return {
    payloadVersion: 1,
    notificationId: NOTIFICATION_ID,
    bindingId: BINDING_ID,
    sdkResponseId: 'sdk-response-1',
    ...overrides,
  };
}

function normalizedCandidate(overrides = {}) {
  return {
    notificationId: NOTIFICATION_ID,
    bindingId: BINDING_ID,
    sdkResponseId: 'sdk-response-1',
    ...overrides,
  };
}

function harness({ ownerUserId = USER_ID, initialNow = '2026-09-08T04:00:00.000Z' } = {}) {
  const keyValue = new MemoryStorage();
  const storage = new PushOpenStorageRepository(keyValue);
  const sent = [];
  const cleared = [];
  let resolvedOwner = ownerUserId;
  let now = initialNow;
  let lastSdkResponseId = 'sdk-response-1';
  const coordinator = new PushOpenCoordinator({
    storage,
    createMessageId: () => MESSAGE_ID,
    now: () => now,
    resolveBindingOwner: async () => resolvedOwner,
    clearLastResponseIfMatches: (responseId) => {
      if (lastSdkResponseId === responseId) {
        cleared.push(responseId);
        lastSdkResponseId = null;
      }
    },
  });

  return {
    coordinator,
    storage,
    keyValue,
    sent,
    cleared,
    setResolvedOwner(value) { resolvedOwner = value; },
    setNow(value) { now = value; },
    connect() {
      return coordinator.connect((pending, authEpoch) => {
        sent.push({ ...pending, authEpoch });
        return pending.messageId;
      });
    },
  };
}

test('notification response data accepts only the versioned UUID signal', () => {
  assert.deepEqual(parsePushOpenCandidate(candidate()), {
    notificationId: NOTIFICATION_ID,
    bindingId: BINDING_ID,
    sdkResponseId: 'sdk-response-1',
  });
  assert.equal(parsePushOpenCandidate(candidate({ payloadVersion: 2 })), null);
  assert.equal(parsePushOpenCandidate(candidate({ notificationId: '/arbitrary/path' })), null);
  assert.equal(parsePushOpenCandidate({ ...candidate(), url: 'https://evil.example' }), null);
});

test('provider-normalized candidate is accepted by the coordinator', async () => {
  const normalized = parsePushOpenCandidate(candidate());
  assert.ok(normalized);
  const h = harness();

  assert.equal(await h.coordinator.capture(normalized), true);
  assert.equal((await h.storage.load()).pending.length, 1);
});

test('PUSH_OPEN_ACK requires the exact logical message, outcome, and user schema', () => {
  const envelope = {
    version: 1,
    type: 'PUSH_OPEN_ACK',
    messageId: '44444444-4444-4444-8444-444444444444',
    bridgeSessionId: '55555555-5555-4555-8555-555555555555',
    authEpoch: 3,
    payload: { messageId: MESSAGE_ID, outcome: 'COMPLETED', userId: USER_ID },
  };
  assert.deepEqual(
    parseWebFeatureMessage(JSON.stringify(envelope), envelope.bridgeSessionId),
    envelope,
  );
  assert.equal(parseWebFeatureMessage(JSON.stringify({
    ...envelope,
    payload: { ...envelope.payload, outcome: 'RECEIVED' },
  }), envelope.bridgeSessionId), null);
  assert.equal(parseWebFeatureMessage(JSON.stringify({
    ...envelope,
    payload: { ...envelope.payload, extra: true },
  }), envelope.bridgeSessionId), null);
});

test('cold and warm captures deduplicate by SDK response and retain one logical messageId', async () => {
  const h = harness();
  await h.coordinator.capture(normalizedCandidate());
  await h.coordinator.capture(normalizedCandidate());
  assert.equal(h.sent.length, 0);

  await h.connect();
  assert.equal(h.sent.length, 1);
  assert.equal(h.sent[0].messageId, MESSAGE_ID);
  assert.equal(h.sent[0].authEpoch, 0);

  h.coordinator.disconnect();
  await h.connect();
  assert.equal(h.sent.length, 2);
  assert.equal(h.sent[1].messageId, MESSAGE_ID);
  assert.equal(h.sent[1].authEpoch, 0);

  const persisted = await h.storage.load();
  assert.equal(persisted.pending.length, 1);
  assert.equal(persisted.pending[0].ownerUserId, USER_ID);
  assert.ok(h.keyValue.values.has(PUSH_OPEN_STORAGE_KEY));
});

test('authenticated epoch zero is redelivered separately from the login prompt delivery', async () => {
  const h = harness();
  await h.coordinator.capture(normalizedCandidate());
  await h.connect();
  await h.coordinator.acceptAuthState({ phase: 'authenticated', authEpoch: 0, userId: USER_ID });

  assert.equal(h.sent.length, 2);
  assert.deepEqual(h.sent.map((item) => item.authEpoch), [0, 0]);
});

test('an authenticated session receives only its binding owner open at the current epoch', async () => {
  const h = harness();
  await h.coordinator.capture(normalizedCandidate());
  await h.connect();

  await h.coordinator.acceptAuthState({
    phase: 'authenticated', authEpoch: 3, userId: USER_ID + 1,
  });
  assert.equal(h.sent.length, 1);

  await h.coordinator.acceptAuthState({
    phase: 'authenticated', authEpoch: 4, userId: USER_ID,
  });
  assert.equal(h.sent.length, 2);
  assert.equal(h.sent[1].messageId, MESSAGE_ID);
  assert.equal(h.sent[1].authEpoch, 4);
});

test('unknown owner is held after login until native binding history can resolve it', async () => {
  const h = harness({ ownerUserId: null });
  await h.coordinator.capture(normalizedCandidate());
  await h.connect();
  await h.coordinator.acceptAuthState({ phase: 'authenticated', authEpoch: 3, userId: USER_ID });
  assert.equal(h.sent.length, 1);

  h.setResolvedOwner(USER_ID);
  await h.coordinator.acceptAuthState({ phase: 'authenticated', authEpoch: 4, userId: USER_ID });
  assert.equal(h.sent.length, 2);
  assert.equal(h.sent[1].authEpoch, 4);
  assert.equal((await h.storage.load()).pending[0].ownerUserId, USER_ID);
});

test('ACK removes pending only for the current authenticated owner and durable removal success', async () => {
  const h = harness();
  await h.coordinator.capture(normalizedCandidate());
  await h.connect();
  await h.coordinator.acceptAuthState({ phase: 'authenticated', authEpoch: 3, userId: USER_ID });

  assert.equal(await h.coordinator.acceptAck({
    messageId: MESSAGE_ID, outcome: 'UNAVAILABLE', userId: USER_ID + 1,
  }, 3), false);
  assert.equal((await h.storage.load()).pending.length, 1);

  h.keyValue.failNextWrite = true;
  await assert.rejects(h.coordinator.acceptAck({
    messageId: MESSAGE_ID, outcome: 'COMPLETED', userId: USER_ID,
  }, 3), /simulated durable failure/);
  assert.equal((await h.storage.load()).pending.length, 1);

  assert.equal(await h.coordinator.acceptAck({
    messageId: MESSAGE_ID, outcome: 'COMPLETED', userId: USER_ID,
  }, 3), true);
  assert.deepEqual(h.cleared, ['sdk-response-1']);
  assert.equal((await h.storage.load()).pending.length, 0);
});

test('a newer SDK response can remain when the clear adapter compares identifiers', async () => {
  let currentSdkResponseId = 'sdk-response-2';
  const keyValue = new MemoryStorage();
  const storage = new PushOpenStorageRepository(keyValue);
  const coordinator = new PushOpenCoordinator({
    storage,
    createMessageId: () => MESSAGE_ID,
    now: () => '2026-09-08T04:00:00.000Z',
    resolveBindingOwner: async () => USER_ID,
    clearLastResponseIfMatches: (expected) => {
      if (currentSdkResponseId === expected) currentSdkResponseId = null;
    },
  });
  await coordinator.capture(normalizedCandidate());
  await coordinator.connect((pending) => pending.messageId);
  await coordinator.acceptAuthState({ phase: 'authenticated', authEpoch: 3, userId: USER_ID });
  await coordinator.acceptAck({ messageId: MESSAGE_ID, outcome: 'COMPLETED', userId: USER_ID }, 3);

  assert.equal(currentSdkResponseId, 'sdk-response-2');
  assert.equal((await storage.load()).pending.length, 0);
});

test('binding owner history survives account changes for 90 days and then expires', async () => {
  const keyValue = new MemoryStorage();
  const storage = new PushOpenStorageRepository(keyValue);
  await storage.recordBindingOwner(BINDING_ID, USER_ID, '2026-06-10T00:00:00.000Z');

  assert.equal(
    await storage.findBindingOwner(BINDING_ID, '2026-09-07T23:59:59.000Z'),
    USER_ID,
  );
  assert.equal(
    await storage.findBindingOwner(BINDING_ID, '2026-09-09T00:00:00.001Z'),
    null,
  );

  const replacementBinding = '66666666-6666-4666-8666-666666666666';
  await storage.recordBindingOwner(replacementBinding, USER_ID + 1, '2026-09-09T00:00:00.001Z');
  const state = await storage.load();
  assert.deepEqual(state.bindingOwners.map((item) => item.bindingId), [replacementBinding]);
});

test('last registration ACK owner fallback is accepted only through the 90-day boundary', () => {
  const ack = {
    operationId: '77777777-7777-4777-8777-777777777777',
    installationId: '88888888-8888-4888-8888-888888888888',
    userId: USER_ID,
    authEpoch: 3,
    bindingId: BINDING_ID,
    revision: 1,
    completedAt: '2026-06-10T00:00:00.000Z',
  };
  assert.equal(
    resolveRecentRegistrationAckOwner(BINDING_ID, ack, '2026-09-08T00:00:00.000Z'),
    USER_ID,
  );
  assert.equal(
    resolveRecentRegistrationAckOwner(BINDING_ID, ack, '2026-09-08T00:00:00.001Z'),
    null,
  );
});

test('withdrawal durably removes only that account pending opens and owner history', async () => {
  const h = harness();
  await h.storage.recordBindingOwner(BINDING_ID, USER_ID, '2026-09-08T00:00:00.000Z');
  await h.coordinator.capture(normalizedCandidate());

  await h.coordinator.handleWithdrawal(USER_ID);

  const state = await h.storage.load();
  assert.equal(state.pending.length, 0);
  assert.equal(state.bindingOwners.length, 0);
  assert.deepEqual(h.cleared, ['sdk-response-1']);
});

test('pending open is retained through 90 days and purged with matching SDK state after that', async () => {
  const boundary = harness({ initialNow: '2026-06-10T00:00:00.000Z' });
  await boundary.coordinator.capture(normalizedCandidate());
  boundary.setNow('2026-09-08T00:00:00.000Z');
  await boundary.connect();
  assert.equal((await boundary.storage.load()).pending.length, 1);
  assert.equal(boundary.sent.length, 1);
  assert.deepEqual(boundary.cleared, []);

  const expired = harness({ initialNow: '2026-06-10T00:00:00.000Z' });
  await expired.coordinator.capture(normalizedCandidate());
  expired.setNow('2026-09-08T00:00:00.001Z');
  await expired.connect();
  assert.equal((await expired.storage.load()).pending.length, 0);
  assert.equal(expired.sent.length, 0);
  assert.deepEqual(expired.cleared, ['sdk-response-1']);
});

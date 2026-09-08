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

const {
  PushStorageRepository,
} = require('../src/push/pushStorage.ts');
const {
  PushRegistrationCoordinator,
} = require('../src/push/pushRegistrationCoordinator.ts');

const SESSION_ID = '11111111-1111-4111-8111-111111111111';
const USER_ID = 42;
const INSTALLATION_ID = '22222222-2222-4222-8222-222222222222';
const BINDING_ID = '33333333-3333-4333-8333-333333333333';
const TOKEN = 'ExponentPushToken[test-token]';

class MemoryStorage {
  constructor() {
    this.value = null;
    this.failNextWrite = false;
  }

  async getItem() { return this.value; }
  async setItem(_key, value) {
    if (this.failNextWrite) {
      this.failNextWrite = false;
      throw new Error('simulated durable failure');
    }
    this.value = value;
  }
}

function installation() {
  return {
    installationId: INSTALLATION_ID,
    installationKey: 'a'.repeat(43),
    createdAt: '2026-09-07T00:00:00.000Z',
    tokenVersion: 0,
  };
}

function harness(permission = 'GRANTED', overrides = {}) {
  const storage = new MemoryStorage();
  const repository = new PushStorageRepository(storage);
  const sent = [];
  let uuidSequence = 4;
  const coordinator = new PushRegistrationCoordinator({
    storage: repository,
    createInstallation: overrides.createInstallation ?? (async () => installation()),
    registrationProvider: overrides.registrationProvider ?? {
      resolve: async () => ({
        permission,
        platform: 'IOS',
        pushToken: permission === 'GRANTED' ? TOKEN : null,
      }),
    },
    createMessageId: () => `${String(uuidSequence++).repeat(8)}-${String(uuidSequence).repeat(4)}-4${String(uuidSequence).repeat(3)}-8${String(uuidSequence).repeat(3)}-${String(uuidSequence).repeat(12)}`,
    now: () => '2026-09-07T01:00:00.000Z',
    schedule: overrides.schedule ?? (() => null),
    cancelSchedule: overrides.cancelSchedule ?? (() => {}),
  });

  coordinator.connect(SESSION_ID, (type, payload, authEpoch) => {
    sent.push({ type, payload, authEpoch });
    return `99999999-9999-4999-8999-${String(sent.length).padStart(12, '0')}`;
  });

  return { coordinator, repository, sent, storage };
}

async function authenticateAndRequest(h) {
  await h.repository.getOrCreateInstallation(async () => installation());
  await h.coordinator.acceptAuthState({ authEpoch: 3, phase: 'authenticated', userId: USER_ID });
  await h.coordinator.requestRegistration(3);
}

test('registration queries state, persists one intent, and ACKs only after a successful durable write', async () => {
  const h = harness();
  await authenticateAndRequest(h);

  assert.equal(h.sent[0].type, 'PUSH_STATE_REQUEST');
  assert.equal(h.sent[0].authEpoch, 3);
  assert.equal(h.sent[0].payload.installationKey, 'a'.repeat(43));

  const requestId = h.sent[0].payload.requestId;
  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: {
      requestId,
      outcome: 'NOT_FOUND',
    },
  });

  assert.equal(h.sent[1].type, 'PUSH_DEVICE');
  assert.equal(h.sent[1].payload.pushToken, TOKEN);
  const persistedIntent = await h.repository.load();
  assert.equal(persistedIntent.pendingRegistration.operationId, h.sent[1].payload.operationId);

  h.storage.failNextWrite = true;
  await assert.rejects(h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId: h.sent[1].payload.operationId,
      outcome: 'SUCCESS',
      data: {
        installationId: INSTALLATION_ID,
        revision: 1,
        bindingId: BINDING_ID,
        status: 'ACTIVE',
        userId: USER_ID,
      },
    },
  }), /simulated durable failure/);
  assert.equal(h.sent.some((message) => message.type === 'PUSH_REGISTER_ACK'), false);

  await h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId: h.sent[1].payload.operationId,
      outcome: 'SUCCESS',
      data: {
        installationId: INSTALLATION_ID,
        revision: 1,
        bindingId: BINDING_ID,
        status: 'ACTIVE',
        userId: USER_ID,
      },
    },
  });

  assert.equal(h.sent.at(-1).type, 'PUSH_REGISTER_ACK');
  const registered = await h.repository.load();
  assert.equal(registered.pendingRegistration, null);
  assert.equal(registered.activeBinding.bindingId, BINDING_ID);
  assert.equal(registered.activeBinding.pushToken, TOKEN);

  await h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId: h.sent[1].payload.operationId,
      outcome: 'SUCCESS',
      data: {
        installationId: INSTALLATION_ID,
        revision: 1,
        bindingId: BINDING_ID,
        status: 'ACTIVE',
        userId: USER_ID,
      },
    },
  });
  assert.equal(h.sent.filter((message) => message.type === 'PUSH_REGISTER_ACK').length, 2);
});

test('a missing registration rotates credentials when the last durable ACK proves it was an existing installation', async () => {
  const replacementId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const replacementKey = 'b'.repeat(43);
  const h = harness('GRANTED', {
    createInstallation: async () => ({
      ...installation(),
      installationId: replacementId,
      installationKey: replacementKey,
    }),
  });
  await h.repository.getOrCreateInstallation(async () => installation());
  await h.repository.update((state) => ({
    ...state,
    lastRegistrationAck: {
      operationId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      installationId: INSTALLATION_ID,
      userId: USER_ID,
      bindingId: BINDING_ID,
      revision: 1,
      authEpoch: 2,
      completedAt: '2026-09-07T00:30:00.000Z',
    },
  }));

  await h.coordinator.acceptAuthState({ authEpoch: 3, phase: 'authenticated', userId: USER_ID });
  await h.coordinator.requestRegistration(3);
  assert.equal(h.sent[0].payload.installationId, INSTALLATION_ID);

  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: { requestId: h.sent[0].payload.requestId, outcome: 'NOT_FOUND' },
  });

  assert.equal(h.sent[1].type, 'PUSH_DEVICE');
  assert.equal(h.sent[1].payload.installationId, replacementId);
  assert.equal(h.sent[1].payload.installationKey, replacementKey);
});

test('token provider failures retry only while the same authenticated epoch remains current', async () => {
  const scheduled = [];
  let attempts = 0;
  const h = harness('GRANTED', {
    registrationProvider: {
      resolve: async () => {
        attempts += 1;
        if (attempts === 1) throw new Error('temporary token failure');
        return { permission: 'GRANTED', platform: 'IOS', pushToken: TOKEN };
      },
    },
    schedule: (callback, delayMs) => {
      const item = { callback, delayMs };
      scheduled.push(item);
      return item;
    },
  });
  await h.repository.getOrCreateInstallation(async () => installation());
  await h.coordinator.acceptAuthState({ authEpoch: 3, phase: 'authenticated', userId: USER_ID });
  await h.coordinator.requestRegistration(3);

  assert.equal(attempts, 1);
  assert.equal(scheduled.length, 1);
  assert.equal(scheduled[0].delayMs, 12000);
  scheduled.shift().callback();
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(attempts, 2);
  assert.equal(h.sent[0].type, 'PUSH_STATE_REQUEST');
});

test('lost responses replay the same request and operation while respecting server retry delay', async () => {
  const scheduled = [];
  const h = harness('GRANTED', {
    schedule: (callback, delayMs) => {
      const item = { callback, delayMs, cancelled: false };
      scheduled.push(item);
      return item;
    },
    cancelSchedule: (item) => { item.cancelled = true; },
  });
  await authenticateAndRequest(h);
  const stateRequest = h.sent[0];
  assert.equal(scheduled[0].delayMs, 12000);

  scheduled[0].callback();
  assert.equal(h.sent[1].type, 'PUSH_STATE_REQUEST');
  assert.deepEqual(h.sent[1].payload, stateRequest.payload);

  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: {
      requestId: stateRequest.payload.requestId,
      outcome: 'RETRY',
      errorCode: 'PUSH_RATE_LIMITED',
      retryAfterMs: 60000,
    },
  });
  assert.equal(scheduled.at(-1).delayMs, 60000);

  scheduled.at(-1).callback();
  assert.deepEqual(h.sent.at(-1).payload, stateRequest.payload);
});

test('session ending durably captures the exact binding before ACK and replays revoke without auth', async () => {
  const h = harness();
  await authenticateAndRequest(h);
  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: { requestId: h.sent[0].payload.requestId, outcome: 'NOT_FOUND' },
  });
  await h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId: h.sent[1].payload.operationId,
      outcome: 'SUCCESS',
      data: { installationId: INSTALLATION_ID, revision: 1, bindingId: BINDING_ID, status: 'ACTIVE', userId: USER_ID },
    },
  });

  await h.coordinator.captureSessionEnding({
    messageId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    authEpoch: 3,
    payload: { reason: 'LOGOUT' },
  });

  const persisted = await h.repository.load();
  assert.equal(persisted.activeBinding, null);
  assert.equal(persisted.pendingRevokes.length, 1);
  assert.equal(persisted.pendingRevokes[0].bindingId, BINDING_ID);
  assert.equal(h.sent.at(-2).type, 'SESSION_ENDING_ACK');
  assert.deepEqual(h.sent.at(-2).payload, {
    requestId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    persisted: true,
  });
  assert.equal(h.sent.at(-1).type, 'PUSH_REVOKE');
  assert.equal(h.sent.at(-1).authEpoch, 0);
});

test('an auth epoch change fences a pending registration result from a different account', async () => {
  const h = harness();
  await authenticateAndRequest(h);
  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: { requestId: h.sent[0].payload.requestId, outcome: 'NOT_FOUND' },
  });
  await h.coordinator.acceptAuthState({ authEpoch: 4, phase: 'authenticated', userId: USER_ID + 1 });

  await h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId: h.sent[1].payload.operationId,
      outcome: 'SUCCESS',
      data: { installationId: INSTALLATION_ID, revision: 1, bindingId: BINDING_ID, status: 'ACTIVE', userId: USER_ID },
    },
  });

  const state = await h.repository.load();
  assert.equal(state.activeBinding, null);
  assert.notEqual(state.pendingRegistration, null);
  assert.equal(h.sent.filter((message) => message.type === 'PUSH_REGISTER_ACK').length, 0);
});

test('a new document stays inactive until that document sends a fresh auth state', async () => {
  const h = harness();
  await authenticateAndRequest(h);
  const sentBeforeReload = h.sent.length;

  h.coordinator.disconnect();
  h.coordinator.connect(SESSION_ID, (type, payload, authEpoch) => {
    h.sent.push({ type, payload, authEpoch });
    return 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
  });
  await h.coordinator.refreshRegistration();

  assert.equal(h.sent.length, sentBeforeReload);
});

test('session ending fences a concurrent late registration result before it becomes active', async () => {
  const h = harness();
  await authenticateAndRequest(h);
  await h.coordinator.acceptStateResult({
    authEpoch: 3,
    payload: { requestId: h.sent[0].payload.requestId, outcome: 'NOT_FOUND' },
  });
  const operationId = h.sent[1].payload.operationId;

  const lateResult = h.coordinator.acceptRegistrationResult({
    authEpoch: 3,
    payload: {
      operationId,
      outcome: 'SUCCESS',
      data: { installationId: INSTALLATION_ID, revision: 1, bindingId: BINDING_ID, status: 'ACTIVE', userId: USER_ID },
    },
  });
  const ending = h.coordinator.captureSessionEnding({
    messageId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    authEpoch: 3,
    payload: { reason: 'LOGOUT' },
  });
  await Promise.all([lateResult, ending]);

  const state = await h.repository.load();
  assert.equal(state.activeBinding, null);
  assert.equal(state.pendingRegistration.operationId, operationId);
  assert.equal(h.sent.filter((message) => message.type === 'PUSH_REGISTER_ACK').length, 0);
});

test('a successful revoke removes only its matching durable item', async () => {
  const h = harness();
  await h.repository.getOrCreateInstallation(async () => installation());
  await h.repository.update((state) => ({
    ...state,
    pendingRevokes: [{
      installationId: INSTALLATION_ID,
      installationKey: 'a'.repeat(43),
      operationId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      operationIssuedAt: '2026-09-07T01:00:00.000Z',
      bindingId: BINDING_ID,
      expectedRevision: 1,
    }],
  }));

  await h.coordinator.flushPendingRevokes();
  assert.equal(h.sent[0].type, 'PUSH_REVOKE');
  await h.coordinator.acceptRevokeResult({
    payload: {
      operationId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
      outcome: 'SUCCESS',
      data: { revoked: false },
    },
  });

  assert.equal((await h.repository.load()).pendingRevokes.length, 0);
});

test('anonymous revision conflicts quarantine the original binding instead of guessing a newer revision', async () => {
  const h = harness();
  await h.repository.getOrCreateInstallation(async () => installation());
  const revoke = {
    installationId: INSTALLATION_ID,
    installationKey: 'a'.repeat(43),
    operationId: 'ffffffff-ffff-4fff-8fff-ffffffffffff',
    operationIssuedAt: '2026-09-07T01:00:00.000Z',
    bindingId: BINDING_ID,
    expectedRevision: 1,
  };
  await h.repository.update((state) => ({ ...state, pendingRevokes: [revoke] }));

  await h.coordinator.acceptRevokeResult({
    payload: {
      operationId: revoke.operationId,
      outcome: 'FAILED',
      errorCode: 'PUSH_REVISION_CONFLICT',
    },
  });

  assert.deepEqual((await h.repository.load()).pendingRevokes, [revoke]);
  assert.equal(h.sent.length, 0);
});

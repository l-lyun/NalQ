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
  MAX_BRIDGE_MESSAGE_BYTES,
  NATIVE_MESSAGE_EVENT,
  createHelloMessage,
  createNativeMessageDispatchScript,
  decideAuthState,
  parseAuthStateMessage,
  parseWebReadyMessage,
  serializeNativeMessage,
  utf8ByteLength,
} = require('../src/push/bridgeProtocol.ts');
const {
  PUSH_STORAGE_KEY,
  PushStorageCorruptedError,
  PushStorageRepository,
  encodeBase64Url,
} = require('../src/push/pushStorage.ts');

const READY_ID = '11111111-1111-4111-8111-111111111111';
const HELLO_ID = '22222222-2222-4222-8222-222222222222';
const SESSION_ID = '33333333-3333-4333-8333-333333333333';
const USER_ID = 42;

function webReady(overrides = {}) {
  return JSON.stringify({
    version: 1,
    type: 'WEB_READY',
    messageId: READY_ID,
    bridgeSessionId: null,
    authEpoch: 0,
    payload: { versions: [1] },
    ...overrides,
  });
}

function authState(overrides = {}) {
  return JSON.stringify({
    version: 1,
    type: 'AUTH_STATE',
    messageId: '44444444-4444-4444-8444-444444444444',
    bridgeSessionId: SESSION_ID,
    authEpoch: 3,
    payload: { phase: 'authenticated', authEpoch: 3, userId: USER_ID },
    ...overrides,
  });
}

function installation(id = '55555555-5555-4555-8555-555555555555') {
  return {
    installationId: id,
    installationKey: 'a'.repeat(43),
    createdAt: '2026-09-07T00:00:00.000Z',
    tokenVersion: 0,
  };
}

class MemoryStorage {
  constructor() {
    this.values = new Map();
    this.failNextWrite = false;
  }

  async getItem(key) {
    return this.values.get(key) ?? null;
  }

  async setItem(key, value) {
    if (this.failNextWrite) {
      this.failNextWrite = false;
      throw new Error('simulated write failure');
    }
    this.values.set(key, value);
  }
}

test('WEB_READY is accepted only with the initial null session envelope and a supported version', () => {
  assert.deepEqual(parseWebReadyMessage(webReady()).payload, { versions: [1] });
  assert.equal(parseWebReadyMessage(webReady({ bridgeSessionId: SESSION_ID })), null);
  assert.equal(parseWebReadyMessage(webReady({ authEpoch: 1 })), null);
  assert.equal(parseWebReadyMessage(webReady({ payload: { versions: [] } })), null);
  assert.equal(parseWebReadyMessage(webReady({ unexpected: true })), null);
});

test('bridge size checks count UTF-8 bytes rather than JavaScript code units', () => {
  assert.equal(utf8ByteLength('NalQ'), 4);
  assert.equal(utf8ByteLength('퀴즈'), 6);

  const oversized = JSON.stringify({ text: '가'.repeat(MAX_BRIDGE_MESSAGE_BYTES / 2) });
  assert.ok(oversized.length < MAX_BRIDGE_MESSAGE_BYTES);
  assert.equal(parseWebReadyMessage(oversized), null);
});

test('HELLO correlates to WEB_READY and dispatches a JSON string on the fixed event', () => {
  const hello = createHelloMessage(SESSION_ID, READY_ID, HELLO_ID);
  assert.deepEqual(hello.payload, {
    version: 1,
    bridgeSessionId: SESSION_ID,
    capabilities: [],
    replyTo: READY_ID,
  });

  const serialized = serializeNativeMessage(hello);
  const script = createNativeMessageDispatchScript(serialized);
  assert.ok(script.includes(JSON.stringify(NATIVE_MESSAGE_EVENT)));
  assert.ok(script.includes(JSON.stringify(serialized)));
  assert.ok(script.endsWith('true;'));
});

test('AUTH_STATE rejects another bridge session and stays inactive without push-v1', () => {
  assert.equal(
    parseAuthStateMessage(authState({ bridgeSessionId: '66666666-6666-4666-8666-666666666666' }), SESSION_ID),
    null,
  );

  const parsed = parseAuthStateMessage(authState(), SESSION_ID);
  assert.ok(parsed);
  assert.deepEqual(decideAuthState(null, parsed, []), {
    accepted: false,
    reason: 'capability-not-negotiated',
  });
  assert.equal(
    parseAuthStateMessage(authState({ authEpoch: 4 }), SESSION_ID),
    null,
  );
});

test('AUTH_STATE does not regress or change identity inside one epoch', () => {
  const current = { authEpoch: 3, phase: 'authenticated', userId: USER_ID };
  const regressed = parseAuthStateMessage(
    authState({ authEpoch: 2, payload: { phase: 'authenticated', authEpoch: 2, userId: USER_ID } }),
    SESSION_ID,
  );
  const conflicted = parseAuthStateMessage(
    authState({ payload: { phase: 'authenticated', authEpoch: 3, userId: USER_ID + 1 } }),
    SESSION_ID,
  );
  const replay = parseAuthStateMessage(authState(), SESSION_ID);

  assert.deepEqual(decideAuthState(current, regressed, ['push-v1']), {
    accepted: false,
    reason: 'epoch-regressed',
  });
  assert.deepEqual(decideAuthState(current, conflicted, ['push-v1']), {
    accepted: false,
    reason: 'epoch-conflict',
  });
  assert.deepEqual(decideAuthState(current, replay, ['push-v1']), {
    accepted: true,
    state: current,
  });
});

test('concurrent installation bootstrap creates and persists only one credential', async () => {
  const storage = new MemoryStorage();
  const repository = new PushStorageRepository(storage);
  let createCount = 0;

  const create = async () => {
    createCount += 1;
    await new Promise((resolve) => setTimeout(resolve, 5));
    return installation();
  };

  const [first, second] = await Promise.all([
    repository.getOrCreateInstallation(create),
    repository.getOrCreateInstallation(create),
  ]);

  assert.equal(createCount, 1);
  assert.deepEqual(first, second);
  assert.deepEqual(JSON.parse(storage.values.get(PUSH_STORAGE_KEY)), first);
});

test('failed durable write rejects without reporting an unpersisted installation', async () => {
  const storage = new MemoryStorage();
  storage.failNextWrite = true;
  const repository = new PushStorageRepository(storage);

  await assert.rejects(
    repository.getOrCreateInstallation(async () => installation()),
    /simulated write failure/,
  );
  assert.equal(storage.values.has(PUSH_STORAGE_KEY), false);

  const recovered = await repository.getOrCreateInstallation(async () => installation());
  assert.equal(recovered.installation.installationId, installation().installationId);
});

test('serialized updates retain concurrent pending revoke mutations', async () => {
  const storage = new MemoryStorage();
  const repository = new PushStorageRepository(storage);
  await repository.getOrCreateInstallation(async () => installation());

  const revoke = (suffix) => ({
    operationId: `${suffix}${suffix}${suffix}${suffix}${suffix}${suffix}${suffix}${suffix}-${suffix}${suffix}${suffix}${suffix}-4${suffix}${suffix}${suffix}-8${suffix}${suffix}${suffix}-${suffix.repeat(12)}`,
    operationIssuedAt: '2026-09-07T00:00:00.000Z',
    bindingId: `${suffix.repeat(8)}-${suffix.repeat(4)}-4${suffix.repeat(3)}-8${suffix.repeat(3)}-${suffix.repeat(12)}`,
    expectedRevision: 1,
  });

  await Promise.all([
    repository.update((state) => ({ ...state, pendingRevokes: [...state.pendingRevokes, revoke('6')] })),
    repository.update((state) => ({ ...state, pendingRevokes: [...state.pendingRevokes, revoke('7')] })),
  ]);

  const finalState = await repository.load();
  assert.equal(finalState.pendingRevokes.length, 2);
});

test('corrupt durable state fails closed instead of rotating the installation identity', async () => {
  const storage = new MemoryStorage();
  storage.values.set(PUSH_STORAGE_KEY, '{"version":1,"installation":null}');
  const repository = new PushStorageRepository(storage);
  let factoryCalled = false;

  await assert.rejects(
    repository.getOrCreateInstallation(async () => {
      factoryCalled = true;
      return installation();
    }),
    PushStorageCorruptedError,
  );
  assert.equal(factoryCalled, false);
  assert.equal(storage.values.get(PUSH_STORAGE_KEY), '{"version":1,"installation":null}');
});

test('malformed installation credentials fail closed instead of being treated as durable state', async () => {
  const storage = new MemoryStorage();
  storage.values.set(PUSH_STORAGE_KEY, JSON.stringify({
    version: 1,
    installation: {
      ...installation(),
      installationKey: 'not-32-random-bytes',
      createdAt: 'not-an-instant',
    },
    activeBinding: null,
    pendingRegistration: null,
    pendingRevokes: [],
  }));
  const repository = new PushStorageRepository(storage);

  await assert.rejects(repository.load(), PushStorageCorruptedError);
});

test('base64url encoding emits a padding-free 43-character key for 32 bytes', () => {
  const encoded = encodeBase64Url(Uint8Array.from({ length: 32 }, (_, index) => index));
  assert.equal(encoded.length, 43);
  assert.match(encoded, /^[A-Za-z0-9_-]+$/);
  assert.equal(encoded, 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8');
});

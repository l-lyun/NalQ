const assert = require('node:assert/strict')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')
const uuid = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const installation = { installationId: uuid(1), installationKey: 'A'.repeat(43) }
const device = { ...installation, operationId: uuid(2), operationIssuedAt: '2026-09-07T00:00:00Z',
  expectedRevision: 0, platform: 'IOS', permission: 'GRANTED', pushToken: 'ExpoPushToken[test_token]' }
const settle = () => new Promise((resolve) => setImmediate(resolve))
function envelope(type, payload, epoch) {
  return JSON.stringify({ version: 1, type, messageId: uuid(3), bridgeSessionId: uuid(4), authEpoch: epoch, payload })
}
function setup(api = {}) {
  const calls = []
  const { load } = fixture({ './pushApi': {
    getPushDevice: async (...args) => { calls.push(['get', ...args]); return { outcome: 'NOT_FOUND' } },
    registerPushDevice: async (...args) => { calls.push(['put', ...args]); return { outcome: 'SUCCESS', data: {
      installationId: uuid(1), revision: 1, bindingId: uuid(9), status: 'ACTIVE', userId: 42,
    } } },
    revokePushDevice: async (...args) => { calls.push(['revoke', ...args]); return { outcome: 'SUCCESS', data: { revoked: true } } },
    ...api,
  } })
  const context = load('features/auth/model/authContext.ts')
  const phase = load('features/auth/model/authPhaseStore.ts')
  context.advanceAuthContext(42)
  phase.setAuthPhase('authenticated')
  const sent = []
  let id = 100
  const session = load('shared/native/pushSession.ts').createPushSession({ sessionId: uuid(4),
    send(type, payload, authEpoch) { const messageId = uuid(++id); sent.push({ type, payload, authEpoch, messageId }); return messageId },
  })
  return { session, context, phase, sent, calls, load }
}

test('negotiated session sends identity and a registration request, never auth tokens', async () => {
  const h = setup()
  try {
    await settle()
    assert.deepEqual(h.sent.map(({ type }) => type), ['AUTH_STATE', 'PUSH_REGISTER_REQUEST'])
    assert.deepEqual(h.sent[0].payload, { phase: 'authenticated', userId: 42, authEpoch: 1 })
    h.session.receive(envelope('PUSH_STATE_REQUEST', { ...installation, requestId: uuid(8) }, 1))
    await settle()
    assert.equal(h.calls[0][0], 'get')
    assert.deepEqual(h.sent.at(-1).payload, { requestId: uuid(8), outcome: 'NOT_FOUND' })
  } finally { h.session.stop() }
})

test('stale epochs, unknown fields and other document sessions never dispatch registration', async () => {
  const h = setup()
  try {
    h.session.receive(envelope('PUSH_DEVICE', device, 0))
    h.session.receive(envelope('PUSH_DEVICE', { ...device, accessToken: 'forbidden' }, 1))
    h.session.receive(envelope('PUSH_DEVICE', device, 1).replace(uuid(4), uuid(5)))
    await settle()
    assert.equal(h.calls.length, 0)
  } finally { h.session.stop() }
})

test('one in-flight registration is deduplicated and late results are fenced after account switch', async () => {
  let resolve
  let calls = 0
  const h = setup({ registerPushDevice: async () => { calls++; return new Promise((yes) => { resolve = yes }) } })
  try {
    const raw = envelope('PUSH_DEVICE', device, 1)
    h.session.receive(raw)
    h.session.receive(raw)
    await settle()
    assert.equal(calls, 1)
    h.context.advanceAuthContext(84)
    resolve({ outcome: 'SUCCESS', data: { userId: 42, bindingId: uuid(9), revision: 1 } })
    await settle()
    assert.equal(h.sent.some(({ type }) => type === 'PUSH_REGISTER_RESULT'), false)
  } finally { h.session.stop() }
})

test('logout waits for correlated durable ACK and anonymous revoke uses epoch zero', async () => {
  const h = setup()
  try {
    await settle()
    const end = h.load('shared/native/nativeSessionLifecycle.ts').prepareNativeSessionEnd('LOGOUT')
    const ending = h.sent.at(-1)
    assert.equal(ending.type, 'SESSION_ENDING')
    h.context.advanceAuthContext()
    h.phase.setAuthPhase('anonymous')
    let completed = false
    end.then(() => { completed = true })
    h.session.receive(envelope('SESSION_ENDING_ACK', { requestId: uuid(999), persisted: true }, 1))
    await settle()
    assert.equal(completed, false)
    h.session.receive(envelope('SESSION_ENDING_ACK', { requestId: ending.messageId, persisted: true }, 1))
    await end
    h.session.receive(envelope('PUSH_REVOKE', { ...installation, operationId: uuid(10),
      operationIssuedAt: device.operationIssuedAt, bindingId: uuid(9), expectedRevision: 1 }, 0))
    await settle()
    assert.equal(h.calls.at(-1)[0], 'revoke')
    assert.equal(h.sent.at(-1).type, 'PUSH_REVOKE_RESULT')
    assert.equal(h.sent.at(-1).authEpoch, 0)
  } finally { h.session.stop() }
})

test('missing ACK is bounded; later login on the same document can register again', async () => {
  const h = setup()
  try {
    await settle()
    const end = h.load('shared/native/nativeSessionLifecycle.ts').prepareNativeSessionEnd('LOGOUT')
    h.context.advanceAuthContext()
    h.phase.setAuthPhase('anonymous')
    await end
    h.context.advanceAuthContext(84)
    h.phase.setAuthPhase('authenticated')
    await settle()
    assert.equal(h.sent.at(-1).type, 'PUSH_REGISTER_REQUEST')
    assert.equal(h.sent.at(-1).authEpoch, 3)
  } finally { h.session.stop() }
})

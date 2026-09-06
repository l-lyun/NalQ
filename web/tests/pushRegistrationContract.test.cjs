const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')
const fixture = require('./helpers/loadTs.cjs')
const uuid = (n) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
const flush = () => new Promise((resolve) => setImmediate(resolve))

test('actual app coordinator and web session register then durably revoke on logout', async () => {
  const writes = []
  let serverBinding = null
  const { load } = fixture({ './pushApi': {
    getPushDevice: async () => ({ outcome: 'NOT_FOUND' }),
    registerPushDevice: async (request, context) => {
      writes.push(['register', request, context])
      serverBinding = uuid(9)
      return { outcome: 'SUCCESS', data: { installationId: request.installationId, revision: 1,
        bindingId: serverBinding, status: 'ACTIVE', userId: context.userId } }
    },
    revokePushDevice: async (request) => {
      writes.push(['revoke', request])
      assert.equal(request.bindingId, serverBinding)
      serverBinding = null
      return { outcome: 'SUCCESS', data: { revoked: true } }
    },
  } })
  const app = (name) => load(path.resolve(__dirname, '../../app/src/push', name))
  const protocol = app('bridgeProtocol.ts')
  const { PushStorageRepository } = app('pushStorage.ts')
  const { PushRegistrationCoordinator } = app('pushRegistrationCoordinator.ts')
  const state = new Map()
  const storage = new PushStorageRepository({ getItem: async (key) => state.get(key) ?? null,
    setItem: async (key, value) => { state.set(key, value) } })
  const context = load('features/auth/model/authContext.ts')
  const phase = load('features/auth/model/authPhaseStore.ts')
  context.advanceAuthContext(42)
  phase.setAuthPhase('authenticated')
  let counter = 100
  const nextId = () => uuid(++counter)
  const sessionId = uuid(8)
  const scheduled = new Set()
  const coordinator = new PushRegistrationCoordinator({ storage,
    createInstallation: async () => ({ installationId: uuid(1), installationKey: 'A'.repeat(43),
      createdAt: '2026-09-07T00:00:00Z', tokenVersion: 0 }),
    registrationProvider: { resolve: async () => ({ platform: 'IOS', permission: 'GRANTED', pushToken: 'ExpoPushToken[test]' }) },
    createMessageId: nextId, now: () => '2026-09-07T00:00:00Z',
    schedule: (callback) => { scheduled.add(callback); return callback },
    cancelSchedule: (callback) => scheduled.delete(callback),
  })
  const errors = []
  const deliverToApp = (type, payload, authEpoch) => {
    const messageId = nextId()
    const parsed = protocol.parseWebFeatureMessage(JSON.stringify({ version: 1, type, payload,
      messageId, bridgeSessionId: sessionId, authEpoch }), sessionId)
    assert.ok(parsed, `app must accept ${type}`)
    let work
    if (type === 'AUTH_STATE') work = coordinator.acceptAuthState({ ...payload, userId: payload.userId ?? null })
    if (type === 'PUSH_REGISTER_REQUEST') work = coordinator.requestRegistration(authEpoch)
    if (type === 'PUSH_STATE_RESULT') work = coordinator.acceptStateResult(parsed)
    if (type === 'PUSH_REGISTER_RESULT') work = coordinator.acceptRegistrationResult(parsed)
    if (type === 'SESSION_ENDING') work = coordinator.captureSessionEnding(parsed)
    if (type === 'PUSH_REVOKE_RESULT') work = coordinator.acceptRevokeResult(parsed)
    Promise.resolve(work).catch((error) => errors.push(error))
    return messageId
  }
  const session = load('shared/native/pushSession.ts').createPushSession({ sessionId, send: deliverToApp })
  coordinator.connect(sessionId, (type, payload, authEpoch) => {
    const messageId = nextId()
    session.receive(JSON.stringify({ version: 1, type, payload, authEpoch, messageId, bridgeSessionId: sessionId }))
    return messageId
  })
  try {
    for (let i = 0; i < 20; i++) await flush()
    assert.deepEqual(errors, [])
    const registered = await storage.load()
    assert.equal(registered.activeBinding.bindingId, uuid(9))
    assert.equal(registered.pendingRegistration, null)
    assert.equal(writes[0][0], 'register')
    const logout = load('shared/native/nativeSessionLifecycle.ts').prepareNativeSessionEnd('LOGOUT')
    context.advanceAuthContext()
    phase.setAuthPhase('anonymous')
    await logout
    for (let i = 0; i < 20; i++) await flush()
    assert.deepEqual(errors, [])
    const ended = await storage.load()
    assert.equal(ended.activeBinding, null)
    assert.deepEqual(ended.pendingRevokes, [])
    assert.equal(serverBinding, null)
    assert.deepEqual(writes.map(([type]) => type), ['register', 'revoke'])
  } finally { coordinator.disconnect(); session.stop() }
})

const assert = require('node:assert/strict')
const fs = require('node:fs')
const test = require('node:test')
const ts = require('typescript')
const vm = require('node:vm')

require.extensions['.ts'] = (module, filename) => {
  module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: filename,
  }).outputText, filename)
}

const { startNativeBridge, parseHello } = require('../src/shared/native/nativeBridge.ts')
const native = require('../../app/src/push/bridgeProtocol.ts')
const requestId = '11111111-1111-4111-8111-111111111111'
const sessionId = '22222222-2222-4222-8222-222222222222'
const hello = (overrides = {}) => JSON.stringify({
  version: 1, type: 'HELLO', messageId: '33333333-3333-4333-8333-333333333333',
  bridgeSessionId: sessionId, authEpoch: 0,
  payload: { version: 1, bridgeSessionId: sessionId, capabilities: [], replyTo: requestId },
  ...overrides,
})

function browser({ native = true, topLevel = true } = {}) {
  const listeners = new Map()
  const timers = new Map()
  const messages = []
  let nextTimer = 0
  const target = {
    crypto: { randomUUID: () => requestId },
    addEventListener: (name, listener) => listeners.set(name, listener),
    removeEventListener: (name, listener) => {
      if (listeners.get(name) === listener) listeners.delete(name)
    },
    setTimeout: (callback) => { timers.set(++nextTimer, callback); return nextTimer },
    clearTimeout: (id) => timers.delete(id),
  }
  target.self = target
  target.top = topLevel ? target : {}
  if (native) target.NalQNativeBridge = { postMessage: (value) => messages.push(JSON.parse(value)) }
  return { target, messages, timers, listeners,
    emit: (detail) => listeners.get('nalq:native-message')?.({ detail }),
    tick: () => { const callbacks = [...timers.values()]; timers.clear(); callbacks.forEach((cb) => cb()) },
  }
}

test('ordinary browser and child frames do not open a native channel', () => {
  for (const options of [{ native: false }, { topLevel: false }]) {
    const host = browser(options)
    startNativeBridge(host.target)()
    assert.equal(host.messages.length, 0)
    assert.equal(host.listeners.size, 0)
  }
})

test('WEB_READY retries are bounded and retain their logical message id', () => {
  const host = browser()
  const stop = startNativeBridge(host.target)
  for (let n = 0; n < 10; n++) host.tick()
  assert.equal(host.messages.length, 3)
  assert.ok(host.messages.every((message) => message.messageId === requestId))
  assert.deepEqual(host.messages[0], {
    version: 1, type: 'WEB_READY', messageId: requestId, bridgeSessionId: null,
    authEpoch: 0, payload: { versions: [1] },
  })
  stop()
  assert.equal(host.listeners.size, 0)
  assert.equal(host.timers.size, 0)
})

test('correlated HELLO stops retries without sending auth, credentials or push requests', () => {
  const host = browser()
  const stop = startNativeBridge(host.target)
  host.emit(hello())
  host.emit(hello())
  host.tick()
  assert.equal(host.messages.length, 1)
  assert.equal(host.timers.size, 0)
  stop()
})

test('invalid, oversized, stale and mismatched HELLO messages are rejected', () => {
  const invalid = [null, {}, '{', '한'.repeat(3000), hello({ version: 2 }),
    hello({ authEpoch: -1 }), hello({ bridgeSessionId: requestId }),
    hello({ token: 'never-accepted' }), hello({ type: 'PUSH_DEVICE' }),
    hello({ payload: { version: 1, bridgeSessionId: sessionId, capabilities: [], replyTo: sessionId } }),
    hello({ payload: { version: 1, bridgeSessionId: sessionId, capabilities: 'push-v1', replyTo: requestId } }),
  ]
  for (const value of invalid) assert.equal(parseHello(value, requestId), null)
  assert.equal(parseHello(hello(), requestId).bridgeSessionId, sessionId)
  const host = browser()
  const stop = startNativeBridge(host.target)
  invalid.forEach(host.emit)
  host.tick()
  assert.equal(host.messages.length, 2)
  stop()
})

test('cleanup and transport failure leave the ordinary web lifecycle usable', () => {
  const host = browser()
  host.target.NalQNativeBridge.postMessage = () => { throw new Error('native unavailable') }
  assert.doesNotThrow(() => startNativeBridge(host.target)())
  assert.equal(host.timers.size, 0)
  assert.equal(host.listeners.size, 0)
})

test('actual app and web implementations exchange HELLO through the fixed event script', () => {
  const host = browser({ native: false })
  const origin = 'https://nalq.test'
  const nonce = `${requestId}.${sessionId}`
  host.target.location = { origin }
  host.target.dispatchEvent = (event) => host.listeners.get(event.type)?.(event)
  const context = { window: host.target,
    Event: class { constructor(type) { this.type = type } },
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail } },
  }
  host.target.ReactNativeWebView = { postMessage: (transport) => {
    const raw = native.parseTransportMessage(transport, nonce)
    const ready = native.parseWebReadyMessage(raw)
    assert.ok(ready)
    host.messages.push(ready)
    const reply = native.createHelloMessage(sessionId, ready.messageId, sessionId)
    assert.deepEqual(reply.payload.capabilities, ['push-v1'])
    const serialized = native.serializeNativeMessage(reply)
    assert.equal(parseHello(serialized, ready.messageId).bridgeSessionId, sessionId)
    vm.runInNewContext(native.createNativeMessageDispatchScript(serialized, origin, nonce), context)
  } }
  vm.runInNewContext(native.createMainDocumentBridgeScript(origin, nonce), context)
  const stop = startNativeBridge(host.target)
  host.tick()
  assert.equal(host.messages.length, 1)
  assert.equal(host.timers.size, 0)
  stop()
})

test('remount rejects a delayed HELLO addressed to the previous effect', () => {
  const host = browser()
  startNativeBridge(host.target)()
  host.target.crypto.randomUUID = () => sessionId
  const stop = startNativeBridge(host.target)
  host.emit(hello())
  host.tick()
  assert.equal(host.messages.length, 3)
  stop()
})

test('HTTP development contexts can use getRandomValues without randomUUID', () => {
  const host = browser()
  host.target.crypto = { getRandomValues: (bytes) => bytes.fill(1) }
  const stop = startNativeBridge(host.target)
  assert.ok(native.parseWebReadyMessage(JSON.stringify(host.messages[0])))
  stop()
})

test('raw RN interface never activates push; late main-document facade can restart negotiation', () => {
  const host = browser({ native: false })
  host.target.ReactNativeWebView = { postMessage: () => assert.fail('raw interface must not be used') }
  const stop = startNativeBridge(host.target)
  assert.equal(host.messages.length, 0)
  host.target.NalQNativeBridge = { postMessage: (value) => host.messages.push(JSON.parse(value)) }
  host.listeners.get('nalq:native-ready')()
  assert.equal(host.messages.length, 1)
  stop()
})

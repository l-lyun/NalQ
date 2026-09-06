const EVENT_NAME = 'nalq:native-message'
const MAX_MESSAGE_BYTES = 8 * 1024
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

type NativeWindow = Window & {
  ReactNativeWebView?: { postMessage: (message: string) => void }
}

type Hello = {
  version: 1
  type: 'HELLO'
  messageId: string
  bridgeSessionId: string
  authEpoch: 0
  payload: {
    version: 1
    bridgeSessionId: string
    capabilities: string[]
    replyTo: string
  }
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function exactKeys(value: Record<string, unknown>, keys: string[]) {
  return Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key))
}

function uuid(value: unknown): value is string {
  return typeof value === 'string' && UUID.test(value)
}

function createMessageId(crypto: Crypto): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // getRandomValues also supports the HTTP origins used by the development WebView shell.
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function parseHello(raw: unknown, readyMessageId: string): Hello | null {
  if (typeof raw !== 'string' || raw.length > MAX_MESSAGE_BYTES
    || new TextEncoder().encode(raw).byteLength > MAX_MESSAGE_BYTES) return null

  try {
    const value: unknown = JSON.parse(raw)
    if (!record(value) || !exactKeys(value, [
      'version', 'type', 'messageId', 'bridgeSessionId', 'authEpoch', 'payload',
    ]) || value.version !== 1 || value.type !== 'HELLO' || value.authEpoch !== 0
      || !uuid(value.messageId) || !uuid(value.bridgeSessionId)) return null

    const payload = value.payload
    if (!record(payload) || !exactKeys(payload, ['version', 'bridgeSessionId', 'capabilities', 'replyTo'])
      || payload.version !== 1 || payload.bridgeSessionId !== value.bridgeSessionId
      || payload.replyTo !== readyMessageId || !Array.isArray(payload.capabilities)
      || payload.capabilities.length > 16
      || !payload.capabilities.every((item) => typeof item === 'string' && /^[a-z0-9-]{1,64}$/.test(item))
      || new Set(payload.capabilities).size !== payload.capabilities.length) return null

    return value as Hello
  } catch {
    return null
  }
}

/** Transport negotiation only: no push capability, auth state, tokens or API calls are activated here. */
export function startNativeBridge(target: Window): () => void {
  const host = target as NativeWindow
  const noop = () => {}
  if (host.self !== host.top || typeof host.ReactNativeWebView?.postMessage !== 'function'
    || (typeof host.crypto?.randomUUID !== 'function' && typeof host.crypto?.getRandomValues !== 'function')) return noop

  const transport = host.ReactNativeWebView
  let messageId: string
  try { messageId = createMessageId(host.crypto) } catch { return noop }
  const ready = JSON.stringify({
    version: 1, type: 'WEB_READY', messageId, bridgeSessionId: null,
    authEpoch: 0, payload: { versions: [1] },
  })
  let timer: number | undefined
  let stopped = false
  let attempts = 0

  function stop() {
    stopped = true
    if (timer !== undefined) host.clearTimeout(timer)
    host.removeEventListener(EVENT_NAME, receive)
  }

  function receive(event: Event) {
    if (!stopped && parseHello((event as CustomEvent<unknown>).detail, messageId)) {
      // This foundation release deliberately ends at HELLO, even if a newer app advertises push-v1.
      stop()
    }
  }

  function sendReady() {
    if (stopped) return
    if (attempts >= 3) { stop(); return }
    attempts += 1
    // Arm before sending: a synchronous native response can safely cancel the retry.
    timer = host.setTimeout(sendReady, 1000)
    try { transport.postMessage(ready) } catch { stop() }
  }

  host.addEventListener(EVENT_NAME, receive)
  sendReady()
  return stop
}

import { getAuthContext, isCurrentAuthContext, subscribeAuthContext } from '@/features/auth/model/authContext'
import { getAuthPhase, subscribeAuthPhase } from '@/features/auth/model/authPhaseStore'
import { getPushDevice, registerPushDevice, revokePushDevice } from './pushApi'
import { parseNativePushMessage, type NativePushMessage } from './pushProtocol'
import { installNativeSessionEndingHandler } from './nativeSessionLifecycle'
import type { NativeConnection, NativeSession } from './nativeBridge'

/** All credentials stay in transient request closures; never persisted or logged by the web. */
export function createPushSession(connection: NativeConnection): NativeSession {
  let active = true
  let ending = false
  let endingEpoch: number | null = null
  let syncQueued = false
  let lastAuth = ''
  const requests = new Map<string, Promise<void>>()
  const endings = new Map<string, { finish: () => void }>()
  // A single chain prevents registration/revoke HTTP changes overtaking one another in this session.
  let writes: Promise<void> = Promise.resolve()

  function sendAuth() {
    syncQueued = false
    if (!active) return
    const context = getAuthContext()
    const phase = getAuthPhase()
    const payload = phase === 'authenticated' && context.userId !== null
      ? { phase: 'authenticated', authEpoch: context.authEpoch, userId: context.userId }
      : { phase: phase === 'anonymous' ? 'anonymous' : 'bootstrapping', authEpoch: context.authEpoch }
    const signature = JSON.stringify(payload)
    if (payload.phase === 'authenticated' && endingEpoch !== null && context.authEpoch > endingEpoch) {
      ending = false
      endingEpoch = null
    }
    if (signature === lastAuth) return
    lastAuth = signature
    connection.send('AUTH_STATE', payload, context.authEpoch)
    if (!ending && payload.phase === 'authenticated') {
      connection.send('PUSH_REGISTER_REQUEST', { authEpoch: context.authEpoch }, context.authEpoch)
    }
  }
  function queueAuth() {
    if (syncQueued) return
    syncQueued = true
    queueMicrotask(sendAuth)
  }

  async function execute(message: NativePushMessage) {
    if (!active) return
    if (message.type === 'PUSH_REVOKE') {
      const result = await revokePushDevice(message.payload)
      if (active) connection.send('PUSH_REVOKE_RESULT', { operationId: message.payload.operationId, ...result }, 0)
      return
    }
    if (message.type !== 'PUSH_DEVICE' && message.type !== 'PUSH_STATE_REQUEST') return
    const context = getAuthContext()
    if (ending || context.userId === null || getAuthPhase() !== 'authenticated' || message.authEpoch !== context.authEpoch) return
    const result = message.type === 'PUSH_DEVICE'
      ? await registerPushDevice(message.payload, context)
      : await getPushDevice(message.payload, context)
    if (!active || !isCurrentAuthContext(context)) return
    if (message.type === 'PUSH_DEVICE') {
      connection.send('PUSH_REGISTER_RESULT', { operationId: message.payload.operationId, ...result }, context.authEpoch)
    } else {
      connection.send('PUSH_STATE_RESULT', { requestId: message.payload.requestId, ...result }, context.authEpoch)
    }
  }

  const unsubscribeContext = subscribeAuthContext(queueAuth)
  const unsubscribePhase = subscribeAuthPhase(queueAuth)
  const removeEndingHandler = installNativeSessionEndingHandler((reason) => {
    if (!active || getAuthContext().userId === null) return Promise.resolve()
    ending = true
    const context = getAuthContext()
    endingEpoch = context.authEpoch
    return new Promise<void>((resolve) => {
      const requestId = connection.send('SESSION_ENDING', { reason }, context.authEpoch)
      if (!requestId) { resolve(); return }
      const timer = setTimeout(finish, 1500)
      function finish() { clearTimeout(timer); endings.delete(requestId!); resolve() }
      endings.set(requestId, { finish })
    })
  })
  queueAuth()

  return {
    receive(raw) {
      if (!active) return
      const message = parseNativePushMessage(raw, connection.sessionId)
      if (!message) return
      if (message.type === 'SESSION_ENDING_ACK') {
        // Correlated to the pre-logout epoch; the current auth epoch has already advanced.
        endings.get(message.payload.requestId)?.finish()
        return
      }
      if (message.type === 'PUSH_REGISTER_ACK') return
      const id = message.type === 'PUSH_STATE_REQUEST' ? message.payload.requestId : message.payload.operationId
      const key = `${message.type}:${message.authEpoch}:${id}`
      if (requests.has(key) || requests.size >= 32) return
      const work = writes.then(() => execute(message)).catch(() => {
        // Never expose request config, credentials or untrusted provider response text to diagnostics.
      }).finally(() => { requests.delete(key) })
      requests.set(key, work)
      writes = work
    },
    stop() {
      active = false
      unsubscribeContext()
      unsubscribePhase()
      removeEndingHandler()
      endings.forEach(({ finish }) => finish())
      requests.clear()
    },
  }
}

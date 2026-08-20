const AUTH_SESSION_CHANNEL = 'openmd-auth-session'
const SESSION_ENDED_MESSAGE = 'session-ended'

export function broadcastSessionEnded() {
  if (typeof BroadcastChannel === 'undefined') return

  try {
    const channel = new BroadcastChannel(AUTH_SESSION_CHANNEL)
    channel.postMessage(SESSION_ENDED_MESSAGE)
    channel.close()
  } catch {
    // Cross-tab synchronization is best-effort and must not block local logout.
  }
}

export function listenForSessionEnded(onSessionEnded: () => void) {
  if (typeof BroadcastChannel === 'undefined') return () => undefined

  try {
    const channel = new BroadcastChannel(AUTH_SESSION_CHANNEL)
    const handleMessage = (event: MessageEvent<unknown>) => {
      if (event.data === SESSION_ENDED_MESSAGE) onSessionEnded()
    }

    channel.addEventListener('message', handleMessage)

    return () => {
      channel.removeEventListener('message', handleMessage)
      channel.close()
    }
  } catch {
    return () => undefined
  }
}

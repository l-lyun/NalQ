export type SessionEndReason = 'LOGOUT' | 'WITHDRAWAL'
let handler: ((reason: SessionEndReason) => Promise<void>) | null = null

export function installNativeSessionEndingHandler(next: (reason: SessionEndReason) => Promise<void>) {
  handler = next
  return () => { if (handler === next) handler = null }
}

export function prepareNativeSessionEnd(reason: SessionEndReason): Promise<void> {
  // A missing/old native bridge must never prevent the existing browser logout flow.
  try { return (handler?.(reason) ?? Promise.resolve()).catch(() => {}) } catch { return Promise.resolve() }
}

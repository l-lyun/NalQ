const PENDING_PREFIX = 'openmd.quiz-generation.pending.v1'
const DELIVERED_PREFIX = 'openmd.notification.snackbar-delivered.v1'
const retentionMs = 90 * 24 * 60 * 60 * 1_000
const pendingListeners = new Set<() => void>()
const fallbackClaims = new Map<number, Set<string>>()

export type PendingQuizGeneration = {
  quizSetId: string
  materialId: string
  savedAt: string
}

function pendingKey(userId: number) {
  return `${PENDING_PREFIX}:${userId}`
}

function deliveredKey(userId: number) {
  return `${DELIVERED_PREFIX}:${userId}`
}

function emitPendingChange() {
  pendingListeners.forEach((listener) => listener())
}

export function subscribePendingGeneration(listener: () => void) {
  pendingListeners.add(listener)
  const storageListener = (event: StorageEvent) => {
    if (event.key?.startsWith(PENDING_PREFIX)) listener()
  }
  window.addEventListener('storage', storageListener)
  return () => {
    pendingListeners.delete(listener)
    window.removeEventListener('storage', storageListener)
  }
}

export function savePendingGeneration(userId: number, pending: Omit<PendingQuizGeneration, 'savedAt'>) {
  try {
    localStorage.setItem(pendingKey(userId), JSON.stringify({ ...pending, savedAt: new Date().toISOString() }))
    emitPendingChange()
  } catch {
    // 서버 작업은 계속되므로 로컬 보존 실패가 생성 요청을 막지 않는다.
  }
}

export function loadPendingGeneration(userId: number) {
  try {
    const raw = localStorage.getItem(pendingKey(userId))
    if (!raw) return null
    const value: unknown = JSON.parse(raw)
    if (!value || typeof value !== 'object') throw new Error('invalid')
    const candidate = value as Partial<PendingQuizGeneration>
    if (typeof candidate.quizSetId !== 'string' || typeof candidate.materialId !== 'string' || typeof candidate.savedAt !== 'string') throw new Error('invalid')
    return candidate as PendingQuizGeneration
  } catch {
    localStorage.removeItem(pendingKey(userId))
    return null
  }
}

export function clearPendingGeneration(userId: number, quizSetId?: string) {
  try {
    const current = loadPendingGeneration(userId)
    if (quizSetId && current?.quizSetId !== quizSetId) return
    localStorage.removeItem(pendingKey(userId))
    emitPendingChange()
  } catch {
    // 다음 알림 목록 조회가 terminal 결과를 복구한다.
  }
}

export function claimSnackbarNotifications(userId: number, notificationIds: string[], now = Date.now()) {
  try {
    const raw = localStorage.getItem(deliveredKey(userId))
    const parsed: unknown = raw ? JSON.parse(raw) : []
    const records = Array.isArray(parsed)
      ? parsed.filter((item): item is { id: string; at: number } => Boolean(item) && typeof item.id === 'string' && typeof item.at === 'number' && now - item.at < retentionMs)
      : []
    const delivered = new Set(records.map((item) => item.id))
    const claimed = notificationIds.filter((id) => !delivered.has(id))
    if (claimed.length === 0) return []
    const next = [...claimed.map((id) => ({ id, at: now })), ...records].slice(0, 200)
    localStorage.setItem(deliveredKey(userId), JSON.stringify(next))
    return claimed
  } catch {
    const delivered = fallbackClaims.get(userId) ?? new Set<string>()
    const claimed = notificationIds.filter((id) => !delivered.has(id))
    claimed.forEach((id) => delivered.add(id))
    fallbackClaims.set(userId, delivered)
    return claimed
  }
}

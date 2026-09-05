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

function isPendingQuizGeneration(value: unknown): value is PendingQuizGeneration {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PendingQuizGeneration>
  return typeof candidate.quizSetId === 'string'
    && typeof candidate.materialId === 'string'
    && typeof candidate.savedAt === 'string'
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
    const saved = { ...pending, savedAt: new Date().toISOString() }
    const existing = loadPendingGenerations(userId).filter((item) => item.quizSetId !== pending.quizSetId)
    localStorage.setItem(pendingKey(userId), JSON.stringify([...existing, saved]))
    emitPendingChange()
  } catch {
    // 서버 작업은 계속되므로 로컬 보존 실패가 생성 요청을 막지 않는다.
  }
}

export function loadPendingGenerations(userId: number): PendingQuizGeneration[] {
  try {
    const raw = localStorage.getItem(pendingKey(userId))
    if (!raw) return []
    const value: unknown = JSON.parse(raw)
    if (Array.isArray(value) && value.every(isPendingQuizGeneration)) return value
    if (isPendingQuizGeneration(value)) return [value]
    throw new Error('invalid')
  } catch {
    try {
      localStorage.removeItem(pendingKey(userId))
    } catch {
      // 저장소 접근 자체가 차단된 환경에서는 메모리 상태만 사용한다.
    }
    return []
  }
}

export function clearPendingGeneration(userId: number, quizSetId?: string) {
  try {
    if (!quizSetId) {
      localStorage.removeItem(pendingKey(userId))
      emitPendingChange()
      return
    }
    const remaining = loadPendingGenerations(userId).filter((item) => item.quizSetId !== quizSetId)
    if (remaining.length === 0) localStorage.removeItem(pendingKey(userId))
    else localStorage.setItem(pendingKey(userId), JSON.stringify(remaining))
    emitPendingChange()
  } catch {
    // 다음 알림 목록 조회가 terminal 결과를 복구한다.
  }
}

function claimSnackbarNotificationsFromStorage(userId: number, notificationIds: string[], now: number) {
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

export async function claimSnackbarNotifications(userId: number, notificationIds: string[], now = Date.now()) {
  const claim = () => claimSnackbarNotificationsFromStorage(userId, notificationIds, now)
  if (!navigator.locks) return claim()
  try {
    return await navigator.locks.request(`${DELIVERED_PREFIX}:${userId}`, { mode: 'exclusive' }, claim)
  } catch {
    return claim()
  }
}

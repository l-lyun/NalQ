const AUTO_SHOWN_KEY_PREFIX = 'nalq:onboarding:auto-shown:v1'

type StorageWriter = Pick<Storage, 'getItem' | 'setItem'>

export type AutomaticOnboardingRouteState = {
  automaticOnboarding: true
  userId: number
  admissionId: string
}

let pendingAdmission: AutomaticOnboardingRouteState | null = null

function storageKey(userId: number) {
  return `${AUTO_SHOWN_KEY_PREFIX}:${userId}`
}

function createAdmissionId() {
  return globalThis.crypto?.randomUUID?.()
    ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`
}

export function prepareAutomaticOnboarding(
  userId: number,
  storage: StorageWriter = window.localStorage,
): AutomaticOnboardingRouteState | null {
  try {
    const key = storageKey(userId)
    if (storage.getItem(key) !== null) return null

    storage.setItem(key, 'shown')
    pendingAdmission = {
      automaticOnboarding: true,
      userId,
      admissionId: createAdmissionId(),
    }
    return pendingAdmission
  } catch {
    pendingAdmission = null
    return null
  }
}

export function hasAutomaticOnboardingAdmission(userId: number, state: unknown) {
  if (!pendingAdmission || !state || typeof state !== 'object') return false

  const candidate = state as Partial<AutomaticOnboardingRouteState>
  return candidate.automaticOnboarding === true
    && candidate.userId === userId
    && candidate.admissionId === pendingAdmission.admissionId
}

export function finishAutomaticOnboarding() {
  pendingAdmission = null
}

export function resetAutomaticOnboardingAdmissionForTest() {
  pendingAdmission = null
}

const listeners = new Set<() => void>()
let activeSceneCount = 0

function emitChange() {
  listeners.forEach((listener) => listener())
}

export function getQuizGenerationScenePresence() {
  return activeSceneCount > 0
}

export function subscribeQuizGenerationScenePresence(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function registerQuizGenerationScene() {
  activeSceneCount += 1
  emitChange()
  let registered = true
  return () => {
    if (!registered) return
    registered = false
    activeSceneCount = Math.max(0, activeSceneCount - 1)
    emitChange()
  }
}

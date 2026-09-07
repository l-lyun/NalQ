export type WalkDirection = -1 | 1

export type WalkPhase = 'paused' | 'running'

export type WalkMotionState = {
  direction: WalkDirection
  phase: WalkPhase
  position: number
  walkedMs: number
}

export type WalkMotionAction =
  | { type: 'face'; direction: WalkDirection }
  | { type: 'tick'; elapsedMs: number }
  | { type: 'toggle' }

export const WALK_SPEED_PER_SECOND = 0.14

export const INITIAL_WALK_STATE: WalkMotionState = {
  direction: 1,
  phase: 'running',
  position: 0.12,
  walkedMs: 0,
}

type AdvanceOptions = {
  max?: number
  min?: number
  speedPerSecond?: number
}

export function advanceWalkMotion(
  state: WalkMotionState,
  elapsedMs: number,
  {
    min = 0,
    max = 1,
    speedPerSecond = WALK_SPEED_PER_SECOND,
  }: AdvanceOptions = {},
): WalkMotionState {
  if (state.phase === 'paused' || elapsedMs <= 0 || speedPerSecond <= 0 || max <= min) {
    return state
  }

  const range = max - min
  const offset = Math.min(max, Math.max(min, state.position)) - min
  const currentPhase = state.direction === 1 ? offset : range * 2 - offset
  const distance = speedPerSecond * elapsedMs / 1000
  const nextPhase = (currentPhase + distance) % (range * 2)
  const headingRight = nextPhase < range

  return {
    ...state,
    direction: headingRight ? 1 : -1,
    position: headingRight
      ? min + nextPhase
      : max - (nextPhase - range),
    walkedMs: state.walkedMs + elapsedMs,
  }
}

export function reduceWalkMotion(
  state: WalkMotionState,
  action: WalkMotionAction,
  reducedMotion = false,
): WalkMotionState {
  if (action.type === 'toggle') {
    return {
      ...state,
      phase: state.phase === 'running' ? 'paused' : 'running',
    }
  }

  if (action.type === 'face') {
    return { ...state, direction: action.direction }
  }

  if (reducedMotion) return state
  return advanceWalkMotion(state, action.elapsedMs)
}

export function getWalkFrame(walkedMs: number, frameCount = 4, frameDurationMs = 180) {
  if (frameCount <= 1 || frameDurationMs <= 0) return 0
  return Math.floor(walkedMs / frameDurationMs) % frameCount
}

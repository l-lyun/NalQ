export type OnboardingMoveSource = 'carousel' | 'control'

export function moveOnboardingIndex(
  current: number,
  direction: -1 | 1,
  slideCount: number,
  source: OnboardingMoveSource,
) {
  const index = Math.max(0, Math.min(slideCount - 1, current + direction))
  const reachedBoundary = index === 0 || index === slideCount - 1

  return {
    index,
    focusHeading: source === 'control' && reachedBoundary,
  }
}

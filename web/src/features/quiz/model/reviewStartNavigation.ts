export type ReviewStartSession = {
  reviewSessionId: string
}

type StartReviewForCurrentRouteOptions = {
  sourceAttemptId: string
  createSession: (sourceAttemptId: string) => Promise<ReviewStartSession>
  invalidateReviews: () => Promise<unknown>
  isRouteActive: () => boolean
  navigate: (path: string) => void
}

export async function startReviewForCurrentRoute({
  sourceAttemptId,
  createSession,
  invalidateReviews,
  isRouteActive,
  navigate,
}: StartReviewForCurrentRouteOptions) {
  const session = await createSession(sourceAttemptId)
  await invalidateReviews()
  if (!isRouteActive()) return
  navigate(`/review-sessions/${session.reviewSessionId}`)
}

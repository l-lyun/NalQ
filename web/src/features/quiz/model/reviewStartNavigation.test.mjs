import assert from 'node:assert/strict'
import test from 'node:test'

import { startReviewForCurrentRoute } from './reviewStartNavigation.ts'

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

test('복습 생성 중 결과 route를 떠나면 늦은 응답이 복습 화면으로 이동시키지 않는다', async () => {
  const created = deferred()
  const navigated = []
  let routeActive = true

  const pending = startReviewForCurrentRoute({
    sourceAttemptId: 'attempt-1',
    createSession: () => created.promise,
    invalidateReviews: async () => undefined,
    isRouteActive: () => routeActive,
    navigate: (path) => navigated.push(path),
  })

  routeActive = false
  created.resolve({ reviewSessionId: 'review-1' })
  await pending

  assert.deepEqual(navigated, [])
})

test('결과 route에 머물러 있으면 생성된 복습 화면으로 이동한다', async () => {
  const navigated = []

  await startReviewForCurrentRoute({
    sourceAttemptId: 'attempt-1',
    createSession: async () => ({ reviewSessionId: 'review-1' }),
    invalidateReviews: async () => undefined,
    isRouteActive: () => true,
    navigate: (path) => navigated.push(path),
  })

  assert.deepEqual(navigated, ['/review-sessions/review-1'])
})

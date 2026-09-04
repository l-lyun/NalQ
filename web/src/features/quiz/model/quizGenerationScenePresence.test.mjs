import assert from 'node:assert/strict'
import test from 'node:test'

import {
  getQuizGenerationScenePresence,
  registerQuizGenerationScene,
  subscribeQuizGenerationScenePresence,
} from './quizGenerationScenePresence.ts'

test('실제 생성 scene이 살아 있는 동안만 전역 알림 claim을 미룰 수 있다', () => {
  const snapshots = []
  const unsubscribe = subscribeQuizGenerationScenePresence(() => {
    snapshots.push(getQuizGenerationScenePresence())
  })

  const unregister = registerQuizGenerationScene()
  assert.equal(getQuizGenerationScenePresence(), true)
  unregister()
  assert.equal(getQuizGenerationScenePresence(), false)
  unsubscribe()

  assert.deepEqual(snapshots, [true, false])
})

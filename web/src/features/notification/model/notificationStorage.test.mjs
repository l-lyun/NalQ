import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearPendingGeneration,
  loadPendingGenerations,
  savePendingGeneration,
} from './notificationStorage.ts'

function memoryStorage() {
  const values = new Map()
  return {
    getItem(key) { return values.get(key) ?? null },
    setItem(key, value) { values.set(key, value) },
    removeItem(key) { values.delete(key) },
  }
}

test('사용자의 서로 다른 pending QuizSet을 모두 보존한다', () => {
  globalThis.localStorage = memoryStorage()
  savePendingGeneration(7, { quizSetId: 'quiz-a', materialId: 'material-a' })
  savePendingGeneration(7, { quizSetId: 'quiz-b', materialId: 'material-b' })

  assert.deepEqual(
    loadPendingGenerations(7).map(({ quizSetId, materialId }) => ({ quizSetId, materialId })),
    [
      { quizSetId: 'quiz-a', materialId: 'material-a' },
      { quizSetId: 'quiz-b', materialId: 'material-b' },
    ],
  )
})

test('완료한 QuizSet만 제거하고 나머지 pending을 유지한다', () => {
  globalThis.localStorage = memoryStorage()
  savePendingGeneration(7, { quizSetId: 'quiz-a', materialId: 'material-a' })
  savePendingGeneration(7, { quizSetId: 'quiz-b', materialId: 'material-b' })

  clearPendingGeneration(7, 'quiz-a')

  assert.deepEqual(loadPendingGenerations(7).map((item) => item.quizSetId), ['quiz-b'])
})

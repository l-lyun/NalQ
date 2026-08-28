import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveLearningRoute } from './learningRoutes.ts'

test('학습 route를 실제 화면 뎁스로 구분한다', () => {
  assert.deepEqual(resolveLearningRoute('/learning'), { id: 'main' })
  assert.deepEqual(resolveLearningRoute('/learning/materials'), { id: 'materials' })
  assert.deepEqual(resolveLearningRoute('/learning/materials/material%201'), {
    id: 'material-edit',
    materialId: 'material 1',
  })
  assert.deepEqual(resolveLearningRoute('/learning/quizzes'), { id: 'quizzes' })
  assert.deepEqual(resolveLearningRoute('/learning/new'), { id: 'new-quiz' })
})

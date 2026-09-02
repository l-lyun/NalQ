import assert from 'node:assert/strict'
import test from 'node:test'

import { getLearningRoutePanelClassName, resolveLearningRoute } from './learningRoutes.ts'

test('학습 route를 실제 화면 뎁스로 구분한다', () => {
  assert.deepEqual(resolveLearningRoute('/learning'), { id: 'main' })
  assert.deepEqual(resolveLearningRoute('/learning/materials'), { id: 'materials' })
  assert.deepEqual(resolveLearningRoute('/learning/materials/material%201'), {
    id: 'material-edit',
    materialId: 'material 1',
  })
  assert.deepEqual(resolveLearningRoute('/learning/quizzes'), { id: 'quizzes' })
  assert.deepEqual(resolveLearningRoute('/learning/new'), { id: 'new-quiz' })
  assert.deepEqual(resolveLearningRoute('/learning/import/notion'), { id: 'notion-import' })
  assert.deepEqual(resolveLearningRoute('/learning/materials/new'), { id: 'material-create' })
})

test('최상위 학습 화면에는 내부 route 진입 모션을 중첩하지 않는다', () => {
  assert.equal(getLearningRoutePanelClassName('main'), 'learning-route-panel')
  assert.match(getLearningRoutePanelClassName('new-quiz'), /learning-route-panel--enter/)
})

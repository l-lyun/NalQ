import assert from 'node:assert/strict'
import test from 'node:test'

import {
  getLearningRoutePanelClassName,
  resolveLearningMaterialsReturnTo,
  resolveLearningRoute,
} from './learningRoutes.ts'

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

test('학습자료 목록의 안전한 복귀 경로만 허용한다', () => {
  assert.equal(resolveLearningMaterialsReturnTo('/learning/materials'), '/learning/materials')
  assert.equal(
    resolveLearningMaterialsReturnTo('/learning/materials?query=자료&page=2&expanded=material-1'),
    '/learning/materials?query=%EC%9E%90%EB%A3%8C&page=2&expanded=material-1',
  )
  assert.equal(resolveLearningMaterialsReturnTo('/learning/materials/'), '/learning/materials')
  assert.equal(resolveLearningMaterialsReturnTo('/learning/materials/new'), undefined)
  assert.equal(resolveLearningMaterialsReturnTo('https://example.com/learning/materials'), undefined)
  assert.equal(resolveLearningMaterialsReturnTo('//example.com/learning/materials'), undefined)
  assert.equal(resolveLearningMaterialsReturnTo('/learning/materials#unexpected'), undefined)
  assert.equal(resolveLearningMaterialsReturnTo(null), undefined)
})

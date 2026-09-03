import assert from 'node:assert/strict'
import test from 'node:test'

import {
  getLearningRoutePanelClassName,
  readLearningCreateReturnState,
  resolveLearningMaterialsReturnTo,
  resolveLearningQuizzesReturnTo,
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

test('퀴즈 목록의 탐색 맥락만 안전한 생성 복귀 경로로 허용한다', () => {
  assert.equal(
    resolveLearningQuizzesReturnTo('/learning/quizzes?query=운영체제&page=2&expanded=q1,q2'),
    '/learning/quizzes?query=%EC%9A%B4%EC%98%81%EC%B2%B4%EC%A0%9C&page=2&expanded=q1,q2',
  )
  assert.equal(resolveLearningQuizzesReturnTo('/learning/new'), undefined)
  assert.equal(resolveLearningQuizzesReturnTo('https://example.com/learning/quizzes'), undefined)
  assert.equal(resolveLearningQuizzesReturnTo('/learning/quizzes#unexpected'), undefined)
})

test('생성 화면 복귀 상태에서 안전한 경로와 유효한 스크롤 위치만 읽는다', () => {
  assert.deepEqual(
    readLearningCreateReturnState({
      returnTo: '/learning/quizzes?query=자료&page=3&expanded=q1',
      returnScrollTop: 480,
    }),
    {
      returnTo: '/learning/quizzes?query=%EC%9E%90%EB%A3%8C&page=3&expanded=q1',
      returnScrollTop: 480,
    },
  )
  assert.deepEqual(
    readLearningCreateReturnState({ returnTo: '//evil.example/learning/quizzes', returnScrollTop: -1 }),
    {},
  )
})

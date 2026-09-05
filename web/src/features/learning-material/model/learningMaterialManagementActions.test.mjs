import assert from 'node:assert/strict'
import test from 'node:test'

import { getLearningMaterialManagementActions } from './learningMaterialManagementActions.ts'

test('펼친 학습자료 카드는 퀴즈 만들기와 학습자료 수정 행동을 제공한다', () => {
  assert.deepEqual(
    getLearningMaterialManagementActions('EDITABLE', false),
    [
      { id: 'create-quiz', label: '퀴즈 만들기', disabled: false },
      { id: 'edit', label: '학습자료 수정', disabled: false },
    ],
  )
})

test('문제 생성 중에는 퀴즈 만들기만 잠그고 자료 수정은 유지한다', () => {
  assert.deepEqual(
    getLearningMaterialManagementActions('LOCKED_GENERATING', false),
    [
      { id: 'create-quiz', label: '퀴즈 만들기', disabled: true },
      { id: 'edit', label: '학습자료 수정', disabled: false },
    ],
  )
})

test('페이지 전환 중에는 이전 페이지 자료의 행동을 잠근다', () => {
  assert.deepEqual(
    getLearningMaterialManagementActions('EDITABLE', true),
    [
      { id: 'create-quiz', label: '퀴즈 만들기', disabled: true },
      { id: 'edit', label: '학습자료 수정', disabled: true },
    ],
  )
})

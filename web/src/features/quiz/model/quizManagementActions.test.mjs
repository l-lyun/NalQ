import assert from 'node:assert/strict'
import test from 'node:test'

import {
  resolveQuizManagementActions,
  resolveQuizManagementActionState,
} from './quizManagementActions.ts'

const quiz = {
  quizSetId: 'quiz-1',
  quizTitle: '운영체제 퀴즈',
  materialId: 'material-1',
  materialTitle: '운영체제',
  status: 'READY',
  questionCount: 10,
  createdAt: '2026-08-20T00:00:00Z',
  updatedAt: '2026-08-20T00:00:00Z',
  lastAttemptAt: null,
}

test('일반 MAIN 퀴즈에는 이어서 풀기를 만들지 않는다', () => {
  const actions = resolveQuizManagementActions(quiz, null)
  assert.deepEqual(actions.map((action) => action.label), ['퀴즈 풀기'])
  assert.equal(actions.some((action) => action.label.includes('이어서')), false)
})

test('미완료 서술형 회차만 자기평가 이어서 하기로 연결한다', () => {
  const pending = {
    attemptId: 'attempt-1',
    quizSetId: quiz.quizSetId,
    status: 'SELF_ASSESSMENT_REQUIRED',
    pendingEssayQuestionIds: ['question-4'],
  }
  const actions = resolveQuizManagementActions(quiz, pending)
  assert.deepEqual(actions, [
    { label: '자기평가 이어하기', path: '/quiz-sets/quiz-1', primary: true },
  ])
})

test('활성 복습은 결과 보기와 활성 세션 재개를 함께 제공한다', () => {
  const latest = {
    sourceAttemptId: 'attempt-1',
    quizSetId: quiz.quizSetId,
    attemptNumber: 2,
    quizTitle: quiz.quizTitle,
    materialTitle: quiz.materialTitle,
    completedAt: '2026-08-20T00:00:00Z',
    totalQuestionCount: 10,
    reviewQuestionCount: 3,
    activeReviewSessionId: 'review-1',
  }
  const actions = resolveQuizManagementActions(quiz, null, latest)
  assert.deepEqual(actions.map((action) => action.label), ['결과 보기', '복습 이어하기'])
  assert.equal(actions[1]?.path, '/review-sessions/review-1')
})

test('완료 이력만 있는 일반 MAIN은 전체 다시 풀기로 표현한다', () => {
  const actions = resolveQuizManagementActions(
    { ...quiz, lastAttemptAt: '2026-08-20T00:00:00Z' },
    null,
  )
  assert.deepEqual(actions.map((action) => action.label), ['전체 다시 풀기'])
  assert.equal(actions.some((action) => action.label.includes('이어서')), false)
})

test('최신 복습 조회가 끝나기 전에는 퀴즈 행동을 보류한다', () => {
  assert.equal(resolveQuizManagementActionState('ready', 'loading'), 'loading')
  assert.equal(resolveQuizManagementActionState('ready', 'error'), 'error')
  assert.equal(resolveQuizManagementActionState('ready', 'ready'), 'ready')
})

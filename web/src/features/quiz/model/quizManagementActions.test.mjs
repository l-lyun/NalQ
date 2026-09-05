import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createNewMainQuizDestination,
  parseExpandedQuizIds,
  resolveQuizSetInitialScene,
  resolvePendingSelfAssessmentForQuizEntry,
  toggleExpandedQuizId,
} from './quizManagementActions.ts'

test('내 퀴즈 펼침 상태는 URL의 쉼표 구분 ID Set으로 여러 카드를 보존한다', () => {
  const initial = parseExpandedQuizIds('quiz-1,quiz-2')
  assert.deepEqual([...initial], ['quiz-1', 'quiz-2'])

  const collapsed = toggleExpandedQuizId(initial, 'quiz-1')
  assert.deepEqual([...collapsed], ['quiz-2'])
  assert.deepEqual([...initial], ['quiz-1', 'quiz-2'])

  const expanded = toggleExpandedQuizId(collapsed, 'quiz-3')
  assert.deepEqual([...expanded], ['quiz-2', 'quiz-3'])
})

test('내 퀴즈의 퀴즈 풀기는 새 MAIN 회차 intent를 만든다', () => {
  assert.deepEqual(createNewMainQuizDestination('quiz-1'), {
    path: '/quiz-sets/quiz-1',
    state: { restartMain: true },
  })
})

test('전체 문제 다시 풀기 진입은 미완료 자기평가 자동 재개를 우회한다', () => {
  const pending = {
    attemptId: 'attempt-1',
    quizSetId: 'quiz-1',
    status: 'SELF_ASSESSMENT_REQUIRED',
    pendingEssayQuestionIds: ['question-4'],
  }

  assert.equal(resolvePendingSelfAssessmentForQuizEntry(pending, true), null)
  assert.equal(resolvePendingSelfAssessmentForQuizEntry(pending, false), pending)
})

test('기존 퀴즈를 새 MAIN 회차로 다시 풀 때는 준비 안내 없이 첫 문제로 바로 진입한다', () => {
  assert.equal(resolveQuizSetInitialScene('READY', null, true), 'SOLVING')
  assert.equal(resolveQuizSetInitialScene('READY', null, false), 'READY')
  assert.equal(resolveQuizSetInitialScene('GENERATING', null, true), 'GENERATION')
  assert.equal(
    resolveQuizSetInitialScene('READY', { attemptId: 'attempt-1' }, true),
    'SELF_ASSESSMENT',
  )
})

import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createNewMainQuizDestination,
  parseExpandedQuizIds,
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

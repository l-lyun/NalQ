import assert from 'node:assert/strict'
import test from 'node:test'

import {
  resolveRecentQuizAction,
  resolveReviewCandidateAction,
} from './learningReviewActions.ts'

const recent = {
  sourceAttemptId: 'attempt-1',
  quizSetId: 'quiz-1',
  attemptNumber: 2,
  quizTitle: '운영체제 퀴즈',
  materialTitle: '운영체제',
  completedAt: '2026-08-28T00:00:00Z',
  totalQuestionCount: 10,
  reviewQuestionCount: 3,
  activeReviewSessionId: null,
}

test('최근 퀴즈는 자기평가, 활성 복습, 새 복습, 결과 순으로 행동을 정한다', () => {
  assert.equal(resolveRecentQuizAction(recent, {
    attemptId: 'attempt-pending',
    quizSetId: 'quiz-1',
    status: 'SELF_ASSESSMENT_REQUIRED',
    pendingEssayQuestionIds: ['question-1'],
  })?.label, '채점이 남았어요')
  assert.equal(resolveRecentQuizAction({ ...recent, activeReviewSessionId: 'review-1' }, null)?.label, '틀린 문제 복습하기')
  assert.deepEqual(resolveRecentQuizAction(recent, null), {
    kind: 'start-review',
    label: '틀린 문제 복습하기',
    sourceAttemptId: 'attempt-1',
  })
  assert.equal(resolveRecentQuizAction({ ...recent, reviewQuestionCount: 0 }, null)?.label, '결과 보기')
})

test('복습 후보도 미완료 자기평가와 활성 복습을 새 복습보다 우선한다', () => {
  const candidate = {
    quizSetId: 'quiz-2',
    quizTitle: '자료구조 퀴즈',
    materialTitle: '자료구조',
    sourceAttemptId: 'attempt-2',
    pendingSelfAssessmentAttemptId: null,
    activeReviewSessionId: null,
    reviewQuestionCount: 2,
    lastLearningActivityAt: '2026-08-27T00:00:00Z',
  }
  assert.equal(resolveReviewCandidateAction({
    ...candidate,
    pendingSelfAssessmentAttemptId: 'attempt-pending',
  }).label, '채점이 남았어요')
  assert.equal(resolveReviewCandidateAction({
    ...candidate,
    activeReviewSessionId: 'review-2',
  }).label, '틀린 문제 복습하기')
  assert.deepEqual(resolveReviewCandidateAction(candidate), {
    kind: 'start-review',
    label: '틀린 문제 복습하기',
    sourceAttemptId: 'attempt-2',
  })
})

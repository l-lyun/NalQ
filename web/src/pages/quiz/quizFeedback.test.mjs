import assert from 'node:assert/strict'
import test from 'node:test'

import { adaptQuizResult, adaptReviewResult } from '../../features/quiz/model/quizAdapter.ts'
import { getQuizOutcomeLabel, getQuizResultTitle } from './quizFeedback.ts'

test('PARTIAL 판정은 행동 중심 문구로 노출한다', () => {
  assert.equal(getQuizOutcomeLabel('PARTIAL'), '보완 필요')
})

test('실제 결과 kind로 본 퀴즈와 복습 제목을 구분한다', () => {
  const main = adaptQuizResult({
    attemptId: 'attempt-1',
    quizSetId: 'quiz-1',
    status: 'COMPLETED',
    summary: {
      scoredGrading: { correctQuestionCount: 0, gradedQuestionCount: 0 },
      essaySelfAssessment: { correctCount: 0, partialCount: 0, incorrectCount: 0 },
      reviewQuestionCount: 0,
    },
    questionResults: [],
  })
  const review = adaptReviewResult({
    reviewSessionId: 'review-1',
    sourceAttemptId: 'attempt-1',
    status: 'COMPLETED',
    summary: {},
    questionResults: [],
  })

  assert.equal(main.kind, 'MAIN')
  assert.equal(review.kind, 'REVIEW')
  assert.equal(getQuizResultTitle(main.kind), '채점 결과')
  assert.equal(getQuizResultTitle(review.kind), '복습 결과')
})

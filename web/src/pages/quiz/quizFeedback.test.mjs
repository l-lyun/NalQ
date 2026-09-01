import assert from 'node:assert/strict'
import test from 'node:test'

import { adaptQuizResult, adaptReviewResult } from '../../features/quiz/model/quizAdapter.ts'
import {
  getQuizOutcomeLabel,
  getQuizResultTitle,
  shouldShowQuizReviewAction,
} from './quizFeedback.ts'

test('PARTIAL 판정은 행동 중심 문구로 노출한다', () => {
  assert.equal(getQuizOutcomeLabel('PARTIAL'), '보완 필요')
})

test('실제 결과 kind로 본 퀴즈와 복습 제목을 구분한다', () => {
  const main = adaptQuizResult({
    attemptId: 'attempt-1',
    quizSetId: 'quiz-1',
    status: 'COMPLETED',
    reviewAvailable: true,
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
    reviewAvailable: false,
    summary: {},
    questionResults: [],
  })

  assert.equal(main.kind, 'MAIN')
  assert.equal(review.kind, 'REVIEW')
  assert.equal(getQuizResultTitle(main.kind), '채점 결과')
  assert.equal(getQuizResultTitle(review.kind), '복습 결과')
})

test('과거 MAIN을 원본으로 한 복습 결과도 다음 복습 행동을 노출하지 않는다', () => {
  const result = adaptReviewResult({
    reviewSessionId: 'review-old',
    sourceAttemptId: 'attempt-old',
    status: 'COMPLETED',
    reviewAvailable: false,
    summary: {},
    questionResults: [{
      questionId: 'question-1',
      number: 1,
      type: 'SHORT_ANSWER',
      topic: '자료구조',
      prompt: '스택의 제거 연산은?',
      response: { answer: 'remove' },
      representativeAnswer: { answer: 'pop' },
      outcome: 'INCORRECT',
      explanation: 'pop 연산을 사용합니다.',
      sourceExcerpt: '스택은 pop으로 제거한다.',
    }],
  })

  assert.equal(result.summary.reviewCount, 1)
  assert.equal(shouldShowQuizReviewAction(result), false)
})

test('과거 MAIN 결과는 미해결 문항 수가 남아 있어도 복습 행동을 노출하지 않는다', () => {
  const result = adaptQuizResult({
    attemptId: 'attempt-old',
    quizSetId: 'quiz-1',
    status: 'COMPLETED',
    reviewAvailable: false,
    summary: {
      scoredGrading: { correctQuestionCount: 0, gradedQuestionCount: 1 },
      essaySelfAssessment: { correctCount: 0, partialCount: 0, incorrectCount: 0 },
      reviewQuestionCount: 1,
    },
    questionResults: [],
  })

  assert.equal(result.summary.reviewCount, 1)
  assert.equal(shouldShowQuizReviewAction(result), false)
})

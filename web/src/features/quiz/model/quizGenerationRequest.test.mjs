import assert from 'node:assert/strict'
import test from 'node:test'

import {
  countGenerationPromptCodePoints,
  getQuizGenerationRecoveryMode,
  isQuizGenerationActiveConflict,
  isQuizGenerationContentRevisionConflict,
  toCreateQuizSetRequest,
  trimGenerationPrompt,
} from './quizGenerationRequest.ts'

const base = {
  selectedTypes: ['MULTIPLE_CHOICE'],
  difficulty: 'NORMAL',
  maxQuestionCount: 20,
}

const contentRevision = 'a'.repeat(64)

test('추가 요청과 확인한 본문 revision을 생성 요청에 포함한다', () => {
  assert.deepEqual(toCreateQuizSetRequest({ ...base, generationPrompt: '\u00a0 동시성에 집중해줘 \u2003' }, contentRevision), {
    ...base,
    generationPrompt: '동시성에 집중해줘',
    contentRevision,
  })
})

test('공백뿐인 추가 요청은 payload에서 생략한다', () => {
  assert.deepEqual(toCreateQuizSetRequest({ ...base, generationPrompt: '\u00a0\u2003' }, contentRevision), {
    ...base,
    contentRevision,
  })
})

test('추가 요청 길이는 UTF-16 단위가 아니라 Unicode code point로 센다', () => {
  assert.equal(countGenerationPromptCodePoints('😀'.repeat(300)), 300)
  assert.equal(trimGenerationPrompt('  요청  '), '요청')
})

test('사용자 전역 생성 제한 충돌만 다른 퀴즈 생성 중 상태로 구분한다', () => {
  assert.equal(isQuizGenerationActiveConflict({ code: 'QUIZ_001' }), true)
  assert.equal(isQuizGenerationActiveConflict({ code: 'QUIZ_002' }), false)
  assert.equal(isQuizGenerationActiveConflict(new Error('network')), false)
})

test('학습자료 본문 revision 충돌을 재확인 필요 상태로 구분한다', () => {
  assert.equal(isQuizGenerationContentRevisionConflict({ code: 'QUIZ_003' }), true)
  assert.equal(isQuizGenerationContentRevisionConflict({ code: 'QUIZ_001' }), false)
  assert.equal(isQuizGenerationContentRevisionConflict(new Error('network')), false)
})

test('외부 생성에서 실패하면 저장하지 않은 추가 요청을 빼고 재요청하지 않는다', () => {
  assert.equal(
    getQuizGenerationRecoveryMode({ kind: 'GENERATION_FAILED', retryable: true }),
    'RETURN_TO_CONDITIONS',
  )
  assert.equal(
    getQuizGenerationRecoveryMode({ kind: 'SOURCE_INSUFFICIENT', retryable: false }),
    'RETURN_TO_CONDITIONS',
  )
  assert.equal(
    getQuizGenerationRecoveryMode({ kind: 'STATUS_UNAVAILABLE', retryable: true }),
    'REFRESH_STATUS',
  )
  assert.equal(
    getQuizGenerationRecoveryMode({ kind: 'REQUEST_FAILED', retryable: true }),
    'RETRY_REQUEST',
  )
})

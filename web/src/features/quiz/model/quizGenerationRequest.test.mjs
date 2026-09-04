import assert from 'node:assert/strict'
import test from 'node:test'

import {
  countGenerationPromptCodePoints,
  isQuizGenerationActiveConflict,
  toCreateQuizSetRequest,
  trimGenerationPrompt,
} from './quizGenerationRequest.ts'

const base = {
  selectedTypes: ['MULTIPLE_CHOICE'],
  difficulty: 'NORMAL',
  maxQuestionCount: 20,
}

test('추가 요청은 Unicode 공백을 정리해 생성 요청에 한 번 포함한다', () => {
  assert.deepEqual(toCreateQuizSetRequest({ ...base, generationPrompt: '\u00a0 동시성에 집중해줘 \u2003' }), {
    ...base,
    generationPrompt: '동시성에 집중해줘',
  })
})

test('공백뿐인 추가 요청은 payload에서 생략한다', () => {
  assert.deepEqual(toCreateQuizSetRequest({ ...base, generationPrompt: '\u00a0\u2003' }), base)
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

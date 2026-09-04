import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveQuizRuntimeMode } from './quizRuntimeMode.ts'

test('기본 실행은 개발과 운영 모두 실제 API를 사용한다', () => {
  assert.equal(resolveQuizRuntimeMode(undefined, true), 'api')
  assert.equal(resolveQuizRuntimeMode(undefined, false), 'api')
  assert.equal(resolveQuizRuntimeMode('api', true), 'api')
})

test('fixture는 개발 중 명시적으로 mock을 선택한 경우에만 사용한다', () => {
  assert.equal(resolveQuizRuntimeMode('mock', true), 'mock')
  assert.equal(resolveQuizRuntimeMode('mock', false), 'api')
})

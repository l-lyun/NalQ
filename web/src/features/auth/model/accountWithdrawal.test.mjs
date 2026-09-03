import assert from 'node:assert/strict'
import test from 'node:test'

import { shouldRetryAccountWithdrawal } from './accountWithdrawal.ts'

test('탈퇴 결과가 불명확할 때 같은 mutation payload를 한 번만 다시 확인한다', () => {
  assert.equal(shouldRetryAccountWithdrawal(0, { kind: 'network' }), true)
  assert.equal(shouldRetryAccountWithdrawal(0, { code: 'AUTH_013', status: 503 }), true)
  assert.equal(shouldRetryAccountWithdrawal(0, { status: 500 }), true)
  assert.equal(shouldRetryAccountWithdrawal(1, { kind: 'network' }), false)
})

test('사용자 입력과 인증 오류는 자동 재시도하지 않는다', () => {
  assert.equal(shouldRetryAccountWithdrawal(0, { code: 'AUTH_012', status: 401 }), false)
  assert.equal(shouldRetryAccountWithdrawal(0, { code: 'COMMON_001', status: 400 }), false)
})

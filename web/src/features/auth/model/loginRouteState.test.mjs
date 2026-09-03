import assert from 'node:assert/strict'
import test from 'node:test'

import {
  ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE,
  readLoginRouteState,
} from './loginRouteState.ts'

test('탈퇴 완료 안내 상태를 로그인 경로에서 복원한다', () => {
  assert.deepEqual(
    readLoginRouteState({ notice: ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE }),
    { email: undefined, from: undefined, notice: ACCOUNT_WITHDRAWAL_COMPLETED_NOTICE },
  )
})

test('알 수 없는 안내 상태는 사용자에게 노출하지 않는다', () => {
  assert.equal(readLoginRouteState({ notice: 'UNKNOWN' }).notice, undefined)
})

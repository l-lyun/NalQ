import assert from 'node:assert/strict'
import test from 'node:test'

import { getPublicBackLabel, readPublicReturnPath } from './publicServiceNavigation.ts'

test('uses an explicit internal return path', () => {
  assert.equal(readPublicReturnPath({ returnTo: '/profile' }), '/profile')
  assert.equal(readPublicReturnPath({ returnTo: '/sign-up?step=2' }), '/sign-up?step=2')
})

test('direct and unsafe entries return to the public login page', () => {
  assert.equal(readPublicReturnPath(undefined), '/login')
  assert.equal(readPublicReturnPath({ returnTo: 'https://example.com' }), '/login')
  assert.equal(readPublicReturnPath({ returnTo: '//example.com' }), '/login')
})

test('뒤로가기 이름은 실제 복귀 목적지를 설명한다', () => {
  assert.equal(getPublicBackLabel('/profile'), '마이페이지로 돌아가기')
  assert.equal(getPublicBackLabel('/login'), '로그인 화면으로 돌아가기')
  assert.equal(getPublicBackLabel('/privacy'), '이전 화면으로 돌아가기')
})

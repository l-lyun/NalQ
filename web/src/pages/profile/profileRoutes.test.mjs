import assert from 'node:assert/strict'
import test from 'node:test'

import {
  cameFromProfileMain,
  normalizeProfilePath,
  profileSubPageNavigationState,
} from './profileRoutes.ts'

test('마이페이지 하위 경로의 후행 슬래시를 제거한다', () => {
  assert.equal(normalizeProfilePath('/profile/account/'), '/profile/account')
  assert.equal(normalizeProfilePath('/profile/guide/'), '/profile/guide')
  assert.equal(normalizeProfilePath('/profile/privacy///'), '/profile/privacy')
})

test('마이페이지 메인에서 이동한 경우만 history 뒤로가기를 허용한다', () => {
  assert.equal(cameFromProfileMain(profileSubPageNavigationState), true)
  assert.equal(cameFromProfileMain(null), false)
  assert.equal(cameFromProfileMain({ returnTo: '/profile' }), false)
})

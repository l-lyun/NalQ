import assert from 'node:assert/strict'
import test from 'node:test'

import { getAppTab, isTopLevelTabPath } from './appTabs.ts'

test('마이페이지 하위 route도 마이페이지 탭 소유로 해석한다', () => {
  assert.equal(getAppTab('/profile/account'), 'profile')
  assert.equal(getAppTab('/profile/marketing'), 'profile')
})

test('마이페이지 하위 route에서는 공통 하단 탭을 숨긴다', () => {
  assert.equal(isTopLevelTabPath('/profile'), true)
  assert.equal(isTopLevelTabPath('/profile/account'), false)
})

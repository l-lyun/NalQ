import assert from 'node:assert/strict'
import test from 'node:test'

import { getTabPanelClassNames } from './appTabTransition.ts'

test('탭 순서와 무관하게 새 panel은 오른쪽에서 들어오고 기존 panel은 왼쪽으로 나간다', () => {
  for (const transition of [
    { from: 'home', to: 'learning', navigation: 'forward' },
    { from: 'learning', to: 'home', navigation: 'forward' },
    { from: 'profile', to: 'home', navigation: 'forward' },
  ]) {
    assert.match(getTabPanelClassNames(transition.to, transition.to, transition), /app-tab-panel--from-right/)
    assert.match(getTabPanelClassNames(transition.from, transition.to, transition), /app-tab-panel--to-left/)
  }
})

test('브라우저 pop은 기존 panel과 새 panel에 역방향 class를 적용한다', () => {
  const transition = { from: 'learning', to: 'home', navigation: 'pop' }
  assert.match(getTabPanelClassNames('home', 'home', transition), /app-tab-panel--from-left/)
  assert.match(getTabPanelClassNames('learning', 'home', transition), /app-tab-panel--to-right/)
})

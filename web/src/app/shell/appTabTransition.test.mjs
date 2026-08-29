import assert from 'node:assert/strict'
import test from 'node:test'

import { getTabPanelClassNames } from './appTabTransition.ts'

test('탭 순서와 무관하게 새 panel은 오른쪽에서 들어오고 기존 panel은 왼쪽으로 나간다', () => {
  for (const transition of [
    { from: 'home', to: 'learning' },
    { from: 'learning', to: 'home' },
    { from: 'profile', to: 'home' },
  ]) {
    assert.match(getTabPanelClassNames(transition.to, transition.to, transition), /app-tab-panel--from-right/)
    assert.match(getTabPanelClassNames(transition.from, transition.to, transition), /app-tab-panel--to-left/)
  }
})

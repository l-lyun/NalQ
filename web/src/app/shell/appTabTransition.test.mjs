import assert from 'node:assert/strict'
import test from 'node:test'

import { getTabPanelClassNames } from './appTabTransition.ts'

test('하단 탭 전환은 활성 panel만 즉시 표시하고 전환 animation class를 만들지 않는다', () => {
  assert.equal(getTabPanelClassNames('learning', 'learning'), 'app-tab-panel app-tab-panel--active')
  assert.equal(getTabPanelClassNames('home', 'learning'), 'app-tab-panel')
})

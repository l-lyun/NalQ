import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { moveOnboardingIndex } from './onboardingCarousel.ts'

const pageSource = await readFile(new URL('./OnboardingPage.tsx', import.meta.url), 'utf8')

test('경계 장으로 이동해 누른 버튼이 사라지면 제목으로 포커스를 옮긴다', () => {
  assert.deepEqual(moveOnboardingIndex(1, 1, 3, 'control'), {
    index: 2,
    focusHeading: true,
  })
  assert.deepEqual(moveOnboardingIndex(1, -1, 3, 'control'), {
    index: 0,
    focusHeading: true,
  })
})

test('남아 있는 버튼과 스와이프 이동은 현재 포커스를 유지한다', () => {
  assert.deepEqual(moveOnboardingIndex(0, 1, 3, 'control'), {
    index: 1,
    focusHeading: false,
  })
  assert.deepEqual(moveOnboardingIndex(1, 1, 3, 'carousel'), {
    index: 2,
    focusHeading: false,
  })
})

test('캐러셀 내부의 포커스된 요소에서 방향키로 장을 바꾸면 새 장 제목으로 포커스한다', () => {
  assert.deepEqual(moveOnboardingIndex(1, 1, 3, 'keyboard'), {
    index: 2,
    focusHeading: true,
  })
  assert.match(pageSource, /move\(-1, 'keyboard'\)/)
  assert.match(pageSource, /move\(1, 'keyboard'\)/)
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./NotificationCenter.tsx', import.meta.url), 'utf8')

test('전역 알림은 실제 생성 scene에서 claim하지 않고 나중에 표시할 수 있다', () => {
  const guard = source.indexOf('notifications.data || generationSceneActive) return')
  const claim = source.indexOf('claimSnackbarNotifications(userId')

  assert.notEqual(guard, -1)
  assert.notEqual(claim, -1)
  assert.ok(guard < claim)
  assert.doesNotMatch(source, /isGenerationRoute/)
})

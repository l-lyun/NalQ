import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('./App.tsx', import.meta.url), 'utf8')
const shellSource = await readFile(new URL('./shell/AuthenticatedAppShell.tsx', import.meta.url), 'utf8')

test('일반 화면과 몰입형 퀴즈 route는 인증 후 하나의 알림 provider 경계를 공유한다', () => {
  assert.match(appSource, /element: <AuthGate \/>/)
  assert.match(appSource, /element: <AuthenticatedNotificationRoute \/>/)
  assert.match(appSource, /<AuthenticatedNotificationBoundary>[\s\S]*?<Outlet \/>[\s\S]*?<\/AuthenticatedNotificationBoundary>/)
  assert.equal((appSource.match(/<NotificationCenterProvider>/g) ?? []).length, 1)
})

test('앱 shell은 별도 알림 provider를 만들어 runtime을 중복 실행하지 않는다', () => {
  assert.doesNotMatch(shellSource, /NotificationCenterProvider/)
})

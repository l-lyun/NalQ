import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const shellSource = await readFile(new URL('./AuthenticatedAppShell.tsx', import.meta.url), 'utf8')
const shellStyles = await readFile(new URL('./app-shell.css', import.meta.url), 'utf8')
const homeSource = await readFile(new URL('../../pages/home/HomePage.tsx', import.meta.url), 'utf8')
const learningSource = await readFile(new URL('../../pages/learning/LearningManagementPages.tsx', import.meta.url), 'utf8')
const learningPrimitives = await readFile(new URL('../../pages/learning/components/LearningPrimitives.tsx', import.meta.url), 'utf8')
const profileSource = await readFile(new URL('../../pages/profile/ProfilePage.tsx', import.meta.url), 'utf8')
const profileSubPages = await readFile(new URL('../../pages/profile/ProfileSubPages.tsx', import.meta.url), 'utf8')

test('알림 종은 고정 전역 레이어 대신 활성 화면의 header slot에 한 번만 배치한다', () => {
  assert.match(shellSource, /createPortal\(<NotificationBell \/>, notificationSlot\)/)
  assert.match(shellSource, /\.app-tab-panel--active \[data-app-notification-slot\]/)
  assert.doesNotMatch(shellSource, /app-notification-utility/)
  assert.doesNotMatch(shellStyles, /\.app-notification-utility/)
  assert.doesNotMatch(shellStyles, /\.app-notification-slot[^}]*position:\s*(?:fixed|absolute)/)
})

test('홈·학습·마이페이지와 일반 하위 화면 header가 알림 slot을 제공한다', () => {
  for (const source of [homeSource, learningSource, learningPrimitives, profileSource, profileSubPages]) {
    assert.match(source, /data-app-notification-slot/)
  }
})

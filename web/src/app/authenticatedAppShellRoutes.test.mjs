import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('./App.tsx', import.meta.url), 'utf8')

test('인증된 홈과 다른 최상위 탭은 하나의 앱 shell route를 공유한다', () => {
  const shellRoute = appSource.match(
    /element: <AuthenticatedAppShell \/>,\s*children: \[([\s\S]*?)\]\s*,?\s*\}/,
  )?.[1]

  assert.ok(shellRoute)
  assert.match(shellRoute, /\{ path: '\/', element: null \}/)
  assert.match(shellRoute, /\{ path: '\/learning', element: null \}/)
  assert.match(shellRoute, /\{ path: '\/profile', element: null \}/)
  assert.doesNotMatch(appSource, /function RootEntryRoute/)
})

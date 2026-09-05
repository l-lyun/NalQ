import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./AuthenticatedProfilePage.tsx', import.meta.url), 'utf8')

test('마이페이지 문의하기는 지원 화면으로 이동하고 마이페이지 복귀 경로를 보존한다', () => {
  assert.match(source, /navigate\('\/support', \{ state: \{ returnTo: '\/profile' \} \}\)/)
  assert.doesNotMatch(source, /\/suppor['"]/)
  assert.doesNotMatch(source, /\/prtofile/)
})

test('마이페이지는 배포 commit과 무관한 앱 버전 0.0.0을 표시한다', () => {
  assert.match(source, /const APP_VERSION = '0\.0\.0'/)
  assert.match(source, /appVersion=\{APP_VERSION\}/)
  assert.doesNotMatch(source, /VITE_APP_VERSION|packageMetadata\.version/)
})

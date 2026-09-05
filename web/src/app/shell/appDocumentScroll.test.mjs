import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const shellSource = await readFile(new URL('./AuthenticatedAppShell.tsx', import.meta.url), 'utf8')
const shellStyles = await readFile(new URL('./app-shell.css', import.meta.url), 'utf8')

test('최상위 탭은 브라우저 문서를 스크롤하고 앱 하단 내비게이션은 화면 아래에 유지한다', () => {
  assert.match(shellSource, /app-shell--document-scroll/)
  assert.match(shellStyles, /\.app-shell--document-scroll\s*\{[^}]*height:\s*auto[^}]*overflow:\s*visible/s)
  assert.match(shellStyles, /\.app-shell--document-scroll \.app-tab-panel--active\s*\{[^}]*position:\s*relative/s)
  assert.match(shellStyles, /\.app-shell--document-scroll \.home-main[^}]*overflow-y:\s*visible/s)
  assert.match(shellStyles, /\.app-shell--document-scroll \.app-bottom-navigation-boundary\s*\{[^}]*position:\s*fixed[^}]*bottom:\s*0/s)
})

test('문서 스크롤로 전환해도 탭별 스크롤 위치를 저장하고 복원한다', () => {
  assert.match(shellSource, /tabScrollPositionsRef/)
  assert.match(shellSource, /window\.scrollY/)
  assert.match(shellSource, /window\.scrollTo\(0, nextScrollTop\)/)
})

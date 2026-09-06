import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const landingSource = await readFile(new URL('../landing/PublicLandingPage.tsx', import.meta.url), 'utf8')
const loginSource = await readFile(new URL('../login/LoginPage.tsx', import.meta.url), 'utf8')
const signUpSource = await readFile(new URL('./SignUpPage.tsx', import.meta.url), 'utf8')

test('회원가입 1단계 뒤로가기는 유효한 공개 진입 이력으로 복귀한다', () => {
  assert.match(landingSource, /navigate\('\/sign-up', \{ state: \{ signUpEntry: '\/' \} \}\)/)
  assert.match(loginSource, /signUpEntry: '\/login'/)
  assert.match(signUpSource, /routeState\?\.signUpEntry === '\/' \|\| routeState\?\.signUpEntry === '\/login'/)
  assert.match(signUpSource, /if \(hasValidEntry\)\s*\{\s*navigate\(-1\)/s)
})

test('회원가입 URL 직접 진입의 1단계 뒤로가기는 공개 랜딩으로 대체 이동한다', () => {
  assert.match(signUpSource, /navigate\('\/', \{ replace: true \}\)/)
})

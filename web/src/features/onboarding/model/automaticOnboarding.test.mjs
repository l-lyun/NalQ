import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createAutomaticOnboardingLifecycle,
  finishAutomaticOnboarding,
  getAutomaticOnboardingAdmission,
  hasAutomaticOnboardingAdmission,
  prepareAutomaticOnboarding,
  resetAutomaticOnboardingAdmissionForTest,
} from './automaticOnboarding.ts'

function memoryStorage(initial = new Map()) {
  return {
    getItem(key) {
      return initial.has(key) ? initial.get(key) : null
    },
    setItem(key, value) {
      initial.set(key, value)
    },
  }
}

test('가입 성공 흐름에서 계정별 표시값과 일회성 진입 문맥을 만든다', () => {
  resetAutomaticOnboardingAdmissionForTest()
  const storage = memoryStorage()
  const state = prepareAutomaticOnboarding(7, storage)

  assert.ok(state)
  assert.equal(storage.getItem('nalq:onboarding:auto-shown:v1:7'), 'shown')
  assert.equal(hasAutomaticOnboardingAdmission(7, state), true)
  assert.equal(hasAutomaticOnboardingAdmission(8, state), false)
})

test('이미 표시한 계정과 저장 실패에는 자동 온보딩을 열지 않는다', () => {
  resetAutomaticOnboardingAdmissionForTest()
  const shown = memoryStorage(new Map([['nalq:onboarding:auto-shown:v1:7', 'shown']]))
  assert.equal(prepareAutomaticOnboarding(7, shown), null)

  const blockedStorage = {
    getItem() {
      throw new Error('blocked')
    },
    setItem() {},
  }
  assert.equal(prepareAutomaticOnboarding(8, blockedStorage), null)
})

test('종료한 자동 온보딩은 같은 history state로 다시 열리지 않는다', () => {
  resetAutomaticOnboardingAdmissionForTest()
  const state = prepareAutomaticOnboarding(7, memoryStorage())
  assert.ok(state)

  finishAutomaticOnboarding()
  assert.equal(hasAutomaticOnboardingAdmission(7, state), false)
})

test('인증 전 준비한 admission은 라우트 가드가 조회할 수 있다', () => {
  resetAutomaticOnboardingAdmissionForTest()
  const state = prepareAutomaticOnboarding(7, memoryStorage())

  assert.deepEqual(getAutomaticOnboardingAdmission(), state)
})

test('StrictMode의 effect 재실행은 admission을 지우지 않고 실제 이탈만 종료한다', async () => {
  resetAutomaticOnboardingAdmissionForTest()
  const state = prepareAutomaticOnboarding(7, memoryStorage())
  const lifecycle = createAutomaticOnboardingLifecycle()

  lifecycle.mount()
  lifecycle.unmount()
  lifecycle.mount()
  await Promise.resolve()
  assert.equal(hasAutomaticOnboardingAdmission(7, state), true)

  lifecycle.unmount()
  await Promise.resolve()
  assert.equal(hasAutomaticOnboardingAdmission(7, state), false)
})

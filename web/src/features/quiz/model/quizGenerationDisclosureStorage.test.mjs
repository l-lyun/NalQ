import assert from 'node:assert/strict'
import test from 'node:test'

import {
  confirmQuizGenerationDisclosure,
  getLearningMaterialContentRevision,
  hasConfirmedQuizGenerationDisclosure,
} from './quizGenerationDisclosureStorage.ts'

function memoryStorage() {
  const values = new Map()
  return {
    getItem(key) { return values.get(key) ?? null },
    setItem(key, value) { values.set(key, value) },
    removeItem(key) { values.delete(key) },
  }
}

test('같은 사용자의 같은 학습자료 본문 revision은 한 번 확인한 뒤 다시 묻지 않는다', async () => {
  globalThis.localStorage = memoryStorage()
  const revision = await getLearningMaterialContentRevision('동시성 학습 본문')

  assert.equal(hasConfirmedQuizGenerationDisclosure(7, 'material-1', revision), false)
  confirmQuizGenerationDisclosure(7, 'material-1', revision)
  assert.equal(hasConfirmedQuizGenerationDisclosure(7, 'material-1', revision), true)
})

test('학습자료 본문이 바뀌면 제목과 무관하게 다시 확인한다', async () => {
  globalThis.localStorage = memoryStorage()
  const previousRevision = await getLearningMaterialContentRevision('기존 본문')
  const changedRevision = await getLearningMaterialContentRevision('수정된 본문')

  confirmQuizGenerationDisclosure(7, 'material-1', previousRevision)

  assert.notEqual(previousRevision, changedRevision)
  assert.equal(hasConfirmedQuizGenerationDisclosure(7, 'material-1', changedRevision), false)
})

test('손상된 확인 레코드나 차단된 저장소는 확인 완료로 추측하지 않는다', async () => {
  const revision = await getLearningMaterialContentRevision('본문')
  globalThis.localStorage = {
    getItem() { return '{broken' },
    setItem() { throw new Error('blocked') },
    removeItem() {},
  }

  assert.equal(hasConfirmedQuizGenerationDisclosure(7, 'material-1', revision), false)
  assert.doesNotThrow(() => confirmQuizGenerationDisclosure(7, 'material-1', revision))
})

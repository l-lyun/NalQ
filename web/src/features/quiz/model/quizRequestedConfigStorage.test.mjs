import assert from 'node:assert/strict'
import test from 'node:test'

import {
  loadRequestedConfig,
  saveRequestedConfig,
} from './quizRequestedConfigStorage.ts'

function memoryStorage() {
  const values = new Map()
  return {
    get length() { return values.size },
    key(index) { return [...values.keys()][index] ?? null },
    getItem(key) { return values.get(key) ?? null },
    setItem(key, value) { values.set(key, String(value)) },
    removeItem(key) { values.delete(key) },
  }
}

test('20문제 설정은 복원하지만 사용자 추가 요청은 로컬에 저장하지 않는다', () => {
  globalThis.localStorage = memoryStorage()
  saveRequestedConfig(1, 'quiz-1', {
    selectedTypes: ['MULTIPLE_CHOICE'],
    difficulty: 'NORMAL',
    maxQuestionCount: 20,
    generationPrompt: '저장하면 안 되는 요청',
  })

  assert.deepEqual(loadRequestedConfig(1, 'quiz-1'), {
    selectedTypes: ['MULTIPLE_CHOICE'],
    difficulty: 'NORMAL',
    maxQuestionCount: 20,
  })
  assert.doesNotMatch(localStorage.getItem('openmd.quiz.requested-config.v1:1:quiz-1'), /저장하면 안 되는 요청/)
})

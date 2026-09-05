import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

test('학습자료 관리도 퀴즈와 동일한 명시적 runtime mode를 사용한다', async () => {
  const source = await readFile(new URL('./learningMaterialManagementAdapter.ts', import.meta.url), 'utf8')

  assert.match(source, /import \{ quizRuntimeMode \}/)
  assert.match(source, /learningMaterialManagementMode[^=]*=\s*quizRuntimeMode/)
  assert.doesNotMatch(source, /VITE_LEARNING_MANAGEMENT_API_ENABLED/)
})

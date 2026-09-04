import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const managementSource = await readFile(new URL('./LearningManagementPages.tsx', import.meta.url), 'utf8')
const quizCardSource = await readFile(new URL('./components/QuizManagementCard.tsx', import.meta.url), 'utf8')
const styles = await readFile(new URL('./learning.css', import.meta.url), 'utf8')

test('내 학습자료와 내 퀴즈 카드는 같은 disclosure 접근성 구조를 사용한다', () => {
  assert.match(managementSource, /function MaterialDisclosureCard/)
  assert.match(managementSource, /aria-expanded=\{expanded\}/)
  assert.match(managementSource, /aria-controls=\{detailId\}/)
  assert.match(managementSource, /expanded \? \([\s\S]*?learning-management-actions/)
  assert.match(quizCardSource, /aria-expanded=\{expanded\}/)
  assert.match(quizCardSource, /aria-controls=\{detailId\}/)
})

test('펼친 카드의 두 행동은 320px에서도 같은 너비의 한 행을 유지한다', () => {
  assert.match(styles, /\.learning-management-actions\s*\{[\s\S]*?flex-wrap:\s*nowrap;/)
  assert.match(styles, /\.learning-management-actions > \*\s*\{[\s\S]*?flex:\s*1 1 0;/)
  assert.match(styles, /\.learning-management-actions > \*\s*\{[\s\S]*?min-height:\s*var\(--seed-dimension-x12\);/)
  assert.doesNotMatch(
    styles,
    /@media \(max-width:\s*359px\)[\s\S]*?\.learning-management-actions[\s\S]*?flex-direction:\s*column;/,
  )
})

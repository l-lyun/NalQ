import assert from 'node:assert/strict'
import test from 'node:test'

import {
  countCodePoints,
  isLearningMaterialDraftValid,
  validateLearningMaterialDraft,
} from './learningMaterialDraft.ts'

test('Unicode code point 기준으로 emoji 한 개를 한 글자로 센다', () => {
  assert.equal(countCodePoints('A😀한'), 3)
})

test('제목은 trim 후, 본문은 원문을 보존한 채 비공백과 길이를 검증한다', () => {
  assert.equal(isLearningMaterialDraftValid({ title: ' 제목 ', content: '  본문  ' }), true)
  assert.deepEqual(validateLearningMaterialDraft({ title: '\u3000', content: '\n\t' }), {
    title: '제목을 입력해 주세요.',
    content: '내용을 입력해 주세요.',
  })
})

test('astral 문자가 포함된 제한 경계를 code point로 판정한다', () => {
  assert.equal(isLearningMaterialDraftValid({ title: '😀'.repeat(255), content: '본문' }), true)
  assert.equal(validateLearningMaterialDraft({ title: '😀'.repeat(256), content: '본문' }).title,
    '제목은 255자까지 입력할 수 있어요.')
})

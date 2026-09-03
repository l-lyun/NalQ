import assert from 'node:assert/strict'
import test from 'node:test'

import {
  extractNotionPageId,
  formatNotionRelativeDate,
  getNotionErrorPresentation,
} from './notionImport.ts'

test('노션 URL의 32자리 또는 dashed UUID를 canonical page id로 바꾼다', () => {
  assert.equal(
    extractNotionPageId('https://www.notion.so/Title-0123456789abcdef0123456789abcdef?pvs=4'),
    '01234567-89ab-cdef-0123-456789abcdef',
  )
  assert.equal(
    extractNotionPageId('https://team.notion.site/01234567-89ab-cdef-0123-456789abcdef'),
    '01234567-89ab-cdef-0123-456789abcdef',
  )
  assert.equal(extractNotionPageId('https://example.com/0123456789abcdef0123456789abcdef'), null)
  assert.equal(extractNotionPageId('https://evilnotion.so/0123456789abcdef0123456789abcdef'), null)
  assert.equal(
    extractNotionPageId('https://www.notion.so/%E0%A4%A-0123456789abcdef0123456789abcdef'),
    null,
  )
})

test('상대 날짜는 수정 접미사 없이 표시한다', () => {
  const now = new Date(2026, 8, 2, 12)
  assert.equal(formatNotionRelativeDate(new Date(2026, 8, 2, 1).toISOString(), now), '오늘')
  assert.equal(formatNotionRelativeDate(new Date(2026, 8, 1, 23).toISOString(), now), '어제')
  assert.equal(formatNotionRelativeDate(new Date(2026, 7, 30, 12).toISOString(), now), '3일 전')
})

test('공개 오류 코드는 서버 message가 아니라 code로 사용자 복구 의미를 고른다', () => {
  assert.deepEqual(getNotionErrorPresentation('NOTION_PAGE_NOT_ACCESSIBLE'), {
    message: '이 페이지에 접근할 수 없어요',
    recovery: 'add-access',
  })
})

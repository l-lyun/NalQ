import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const styles = await readFile(new URL('./onboarding.css', import.meta.url), 'utf8')
const conditionsCapture = await readFile(
  new URL('./assets/nalq-guide-conditions.png', import.meta.url),
)

test('작은 높이와 큰 글자에서도 제목·설명·캡처 힌트를 포함한 본문 전체를 스크롤한다', () => {
  const contentRule = styles.match(/\.onboarding-content\s*\{([^}]*)\}/)?.[1]

  assert.ok(contentRule)
  assert.match(contentRule, /overflow-y:\s*auto/)
  assert.doesNotMatch(contentRule, /overflow:\s*hidden/)
})

test('학습 방식 캡처는 문제 만들기 버튼까지 담을 높이를 확보한다', () => {
  assert.deepEqual(
    {
      width: conditionsCapture.readUInt32BE(16),
      height: conditionsCapture.readUInt32BE(20),
    },
    { width: 390, height: 932 },
  )
})

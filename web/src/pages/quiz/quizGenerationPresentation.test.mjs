import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./QuizFlowPage.tsx', import.meta.url), 'utf8')
const styles = readFileSync(new URL('./quiz.css', import.meta.url), 'utf8')

test('전송 확인 dialog는 모바일 gutter를 두고 보관 기간과 예외를 함께 안내한다', () => {
  assert.match(source, /ContentDialog\.Positioner className="quiz-generation-dialog-positioner"/)
  assert.match(
    source,
    /className="quiz-generation-disclosure-description"[\s\S]*?\{'\uD559\uC2B5\uC790\uB8CC \uBCF8\uBB38 \uC804\uCCB4, \uBB38\uC81C \uC720\uD615·\uB09C\uC774\uB3C4·\uBB38\uC81C \uC218,\\n\uC785\uB825\uD55C \uCD94\uAC00 \uC694\uCCAD'\}/,
  )
  assert.match(source, /입력·출력은 통상 최대 30일 보관돼요\./)
  assert.match(source, /법적 의무나 서비스·제3자 보호를 위해 더 오래 보관될 수 있어요\./)
  assert.doesNotMatch(source, /악용 방지 정책에 따라 최대 30일 보관될 수 있어요\./)
  assert.doesNotMatch(source, /className="quiz-generation-retention-notice"/)
  assert.doesNotMatch(styles, /\.quiz-generation-retention-notice/)
  assert.match(styles, /\.quiz-generation-dialog-positioner\s*\{[^}]*padding-inline:/s)
  assert.match(styles, /\.quiz-generation-disclosure-description\s*\{[^}]*white-space:\s*pre-line/s)
})

test('추가 요청 설명은 명시적 두 줄이고 글자 수는 한 줄을 유지한다', () => {
  assert.match(
    source,
    /className="quiz-generation-prompt-description"[\s\S]*?\{'\uBB38제 \uCD9C제 \uCD08점과 \uC2A4타일을 \uBC18영해요\.\\n\uBB38제 \uC720형·\uC218·\uD559습자료 \uADFC거는 \uBC14꿀 \uC218 \uC5C6어요\.'\}/,
  )
  assert.doesNotMatch(source, /quiz-generation-prompt-description-line/)
  assert.match(styles, /\.quiz-generation-prompt-description\s*\{[^}]*min-width:\s*0[^}]*white-space:\s*pre-line/s)
  assert.match(source, /className="quiz-generation-prompt-count"/)
  assert.match(styles, /\.quiz-generation-prompt-count\s*\{[^}]*white-space:\s*nowrap[^}]*flex:\s*0 0 var\(--seed-dimension-x12\)/s)
})

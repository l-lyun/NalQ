import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./QuizFlowPage.tsx', import.meta.url), 'utf8')
const styles = readFileSync(new URL('./quiz.css', import.meta.url), 'utf8')

test('전송 확인 dialog는 모바일 gutter를 두고 30일 안내를 짧게 보여준다', () => {
  assert.match(source, /ContentDialog\.Positioner className="quiz-generation-dialog-positioner"/)
  assert.match(source, /악용 방지 정책에 따라 최대 30일 보관될 수 있어요\./)
  assert.match(styles, /\.quiz-generation-dialog-positioner\s*\{[^}]*padding-inline:/s)
})

test('추가 요청 설명은 명시적 두 줄이고 글자 수는 한 줄을 유지한다', () => {
  assert.match(source, /문제 출제 초점과 스타일을 반영해요\./)
  assert.match(source, /문제 유형·수·학습자료 근거는 바꿀 수 없어요\./)
  assert.match(source, /className="quiz-generation-prompt-count"/)
  assert.match(styles, /\.quiz-generation-prompt-count\s*\{[^}]*white-space:\s*nowrap[^}]*flex-shrink:\s*0/s)
})

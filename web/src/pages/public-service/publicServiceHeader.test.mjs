import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PublicServicePages.tsx', import.meta.url), 'utf8')

test('공개 서비스 화면은 다른 하위 화면과 같은 compact 뒤로가기 헤더를 사용한다', () => {
  assert.match(source, /<Flex as="header" align="center" gap="x2">/)
  assert.match(source, /size="small"[\s\S]*?variant="ghost"[\s\S]*?layout="iconOnly"/)
  assert.match(source, /aria-label=\{getPublicBackLabel\(returnTo\)\}/)
  assert.match(source, /<Icon svg=\{<IconArrowLeftLine \/>\} size="x5" \/>/)
  assert.match(source, /<Text as="h1" textStyle="t10Bold" color="fg\.neutral">\{title\}<\/Text>/)
  assert.doesNotMatch(source, /NalQ로 돌아가기/)
})

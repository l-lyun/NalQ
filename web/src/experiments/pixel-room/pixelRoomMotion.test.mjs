import assert from 'node:assert/strict'
import test from 'node:test'

import {
  advanceWalkMotion,
  getWalkFrame,
  reduceWalkMotion,
} from './pixelRoomMotion.ts'

const runningRight = {
  direction: 1,
  phase: 'running',
  position: 0.9,
  walkedMs: 0,
}

test('오른쪽 벽을 지나면 범위 안에서 남은 거리만큼 왼쪽으로 걷는다', () => {
  const next = advanceWalkMotion(runningRight, 2_000, { speedPerSecond: 0.1 })

  assert.equal(next.direction, -1)
  assert.ok(next.position >= 0 && next.position <= 1)
  assert.ok(Math.abs(next.position - 0.9) < 0.000_001)
})

test('긴 프레임 지연에도 양쪽 벽을 넘지 않고 방향을 계산한다', () => {
  const next = advanceWalkMotion(runningRight, 27_000, { speedPerSecond: 0.1 })

  assert.equal(next.direction, -1)
  assert.ok(Math.abs(next.position - 0.4) < 0.000_001)
})

test('일시정지 중에는 움직이지 않고 재개한 뒤 기존 위치에서 계속 걷는다', () => {
  const paused = reduceWalkMotion(runningRight, { type: 'toggle' })
  const whilePaused = reduceWalkMotion(paused, { type: 'tick', elapsedMs: 1_000 })
  const resumed = reduceWalkMotion(whilePaused, { type: 'toggle' })
  const afterResume = reduceWalkMotion(resumed, { type: 'tick', elapsedMs: 500 })

  assert.strictEqual(whilePaused, paused)
  assert.equal(resumed.phase, 'running')
  assert.notEqual(afterResume.position, resumed.position)
})

test('모션 감소 환경에서는 실행 상태여도 자동 이동을 만들지 않는다', () => {
  const next = reduceWalkMotion(runningRight, { type: 'tick', elapsedMs: 1_000 }, true)

  assert.strictEqual(next, runningRight)
})

test('4프레임은 정해진 간격으로 순환한다', () => {
  assert.deepEqual(
    [0, 180, 360, 540, 720].map((walkedMs) => getWalkFrame(walkedMs)),
    [0, 1, 2, 3, 0],
  )
})

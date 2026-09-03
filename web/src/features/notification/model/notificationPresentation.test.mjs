import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatNotificationTime,
  notificationActionLabel,
  notificationDestination,
  notificationMessage,
} from './notificationPresentation.ts'

const ready = {
  notificationId: 'n1', payloadVersion: 1, type: 'QUIZ_GENERATION_READY',
  quizSetId: 'quiz 1', materialId: 'm1', targetName: '운영체제', failureCode: null,
  actionType: 'FOCUS_QUIZ_IN_LIST', targetAvailable: true, readAt: null,
  createdAt: '2026-09-03T00:00:00Z',
}

test('완료 알림은 내 퀴즈 focus 이동 정보를 만든다', () => {
  assert.equal(notificationMessage(ready), '퀴즈가 완성됐어요.')
  assert.equal(notificationActionLabel(ready), '목록보기')
  assert.equal(notificationDestination(ready), '/learning/quizzes?focus=quiz%201')
})

test('실패 원인에 따라 복구 문구를 구분한다', () => {
  const failed = { ...ready, type: 'QUIZ_GENERATION_FAILED', actionType: 'RECONFIGURE_QUIZ', failureCode: 'SOURCE_INSUFFICIENT' }
  assert.equal(notificationMessage(failed), '학습자료에서 문제를 만들지 못했어요.')
  assert.equal(notificationActionLabel(failed), '자료·조건 확인')
  assert.equal(notificationDestination(failed), '/learning/m1/quiz')
})

test('상대 시각은 분과 시간 단위로 표현한다', () => {
  const now = Date.parse('2026-09-03T02:00:00Z')
  assert.equal(formatNotificationTime('2026-09-03T01:52:00Z', now), '8분 전')
  assert.equal(formatNotificationTime('2026-09-03T00:00:00Z', now), '2시간 전')
})

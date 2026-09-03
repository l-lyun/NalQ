import type { QuizGenerationNotification } from '../api/notification.types'

export function notificationMessage(notification: QuizGenerationNotification) {
  if (notification.type === 'QUIZ_GENERATION_READY') return '퀴즈가 완성됐어요.'
  if (notification.failureCode === 'SOURCE_INSUFFICIENT') return '학습자료에서 문제를 만들지 못했어요.'
  return '퀴즈를 만들지 못했어요. 다시 시도해 주세요.'
}

export function notificationActionLabel(notification: QuizGenerationNotification) {
  if (notification.type === 'QUIZ_GENERATION_READY') return '목록보기'
  if (notification.failureCode === 'SOURCE_INSUFFICIENT') return '자료·조건 확인'
  return '다시 만들기'
}

export function notificationDestination(notification: QuizGenerationNotification) {
  if (notification.actionType === 'FOCUS_QUIZ_IN_LIST') {
    return `/learning/quizzes?focus=${encodeURIComponent(notification.quizSetId)}`
  }
  return `/learning/${encodeURIComponent(notification.materialId)}/quiz`
}

export function formatNotificationTime(createdAt: string, now = Date.now()) {
  const elapsed = Math.max(0, now - new Date(createdAt).getTime())
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (elapsed < minute) return '방금 전'
  if (elapsed < hour) return `${Math.floor(elapsed / minute)}분 전`
  if (elapsed < day) return `${Math.floor(elapsed / hour)}시간 전`
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(createdAt))
}

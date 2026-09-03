import { quizRuntimeMode } from '@/features/quiz/model/quizFeature'

import { getNotifications, readAllNotifications, readNotification } from './notification.api'
import type {
  NotificationPage,
  NotificationReadResult,
  NotificationsReadAllResult,
  QuizGenerationNotification,
} from './notification.types'

export const notificationsEnabled = quizRuntimeMode !== 'disabled'

let mockItems: QuizGenerationNotification[] = [
  {
    notificationId: 'notification-ready-os',
    payloadVersion: 1,
    type: 'QUIZ_GENERATION_READY',
    quizSetId: 'quiz-set-os-essay',
    materialId: 'material-operating-system',
    targetName: '운영체제 서술형 점검',
    failureCode: null,
    actionType: 'FOCUS_QUIZ_IN_LIST',
    targetAvailable: true,
    readAt: null,
    createdAt: new Date(Date.now() - 2 * 60_000).toISOString(),
  },
  {
    notificationId: 'notification-failed-network',
    payloadVersion: 1,
    type: 'QUIZ_GENERATION_FAILED',
    quizSetId: 'quiz-set-failed-network',
    materialId: 'material-network',
    targetName: '네트워크 기초 개념 퀴즈',
    failureCode: 'SOURCE_INSUFFICIENT',
    actionType: 'RECONFIGURE_QUIZ',
    targetAvailable: true,
    readAt: null,
    createdAt: new Date(Date.now() - 8 * 60_000).toISOString(),
  },
]

function ensureAvailable() {
  if (!notificationsEnabled) throw new Error('알림 API가 아직 활성화되지 않았어요.')
}

function currentUnreadCount() {
  return mockItems.filter((item) => item.readAt === null).length
}

export async function listManagedNotifications(cursor?: string, signal?: AbortSignal): Promise<NotificationPage> {
  ensureAvailable()
  if (quizRuntimeMode === 'api') return getNotifications(cursor, signal)
  if (signal?.aborted) throw new DOMException('요청이 취소되었습니다.', 'AbortError')
  const start = cursor ? Number(cursor) : 0
  const safeStart = Number.isInteger(start) && start >= 0 ? start : 0
  const items = mockItems.slice(safeStart, safeStart + 20).map((item) => ({ ...item }))
  const next = safeStart + items.length
  return {
    items,
    unreadCount: currentUnreadCount(),
    nextCursor: next < mockItems.length ? String(next) : null,
    hasNext: next < mockItems.length,
  }
}

export async function readManagedNotification(notificationId: string): Promise<NotificationReadResult> {
  ensureAvailable()
  if (quizRuntimeMode === 'api') return readNotification(notificationId)
  const item = mockItems.find((candidate) => candidate.notificationId === notificationId)
  if (!item) throw new Error('알림을 찾지 못했어요.')
  const readAt = item.readAt ?? new Date().toISOString()
  mockItems = mockItems.map((candidate) => candidate.notificationId === notificationId ? { ...candidate, readAt } : candidate)
  return { notificationId, readAt, unreadCount: currentUnreadCount() }
}

export async function readAllManagedNotifications(throughNotificationId: string): Promise<NotificationsReadAllResult> {
  ensureAvailable()
  if (quizRuntimeMode === 'api') return readAllNotifications(throughNotificationId)
  const boundary = mockItems.findIndex((item) => item.notificationId === throughNotificationId)
  if (boundary < 0) throw new Error('알림 경계를 찾지 못했어요.')
  const readAt = new Date().toISOString()
  let updatedCount = 0
  mockItems = mockItems.map((item, index) => {
    if (index < boundary || item.readAt !== null) return item
    updatedCount += 1
    return { ...item, readAt }
  })
  return { readAt, updatedCount, unreadCount: currentUnreadCount() }
}

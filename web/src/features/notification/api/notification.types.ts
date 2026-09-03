export type NotificationType = 'QUIZ_GENERATION_READY' | 'QUIZ_GENERATION_FAILED'
export type NotificationFailureCode = 'SOURCE_INSUFFICIENT' | 'GENERATION_FAILED'
export type NotificationActionType = 'FOCUS_QUIZ_IN_LIST' | 'RECONFIGURE_QUIZ'

export type QuizGenerationNotification = {
  notificationId: string
  payloadVersion: 1
  type: NotificationType
  quizSetId: string
  materialId: string
  targetName: string
  failureCode: NotificationFailureCode | null
  actionType: NotificationActionType
  targetAvailable: boolean
  readAt: string | null
  createdAt: string
}

export type NotificationPage = {
  items: QuizGenerationNotification[]
  unreadCount: number
  nextCursor: string | null
  hasNext: boolean
}

export type NotificationReadResult = {
  notificationId: string
  readAt: string
  unreadCount: number
}

export type NotificationsReadAllResult = {
  readAt: string
  updatedCount: number
  unreadCount: number
}

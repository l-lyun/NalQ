import type { ApiResponse } from '@/features/auth/api/auth.types'
import { unwrapApiResponse } from '@/shared/api/apiError'
import { protectedApi } from '@/shared/api/protectedApi'

import type {
  NotificationPage,
  NotificationReadResult,
  NotificationsReadAllResult,
} from './notification.types'

export async function getNotifications(cursor?: string, signal?: AbortSignal) {
  const response = await protectedApi.get<ApiResponse<NotificationPage>>('/api/v1/notifications', {
    signal,
    params: cursor ? { cursor } : undefined,
    headers: { 'Cache-Control': 'no-cache' },
  })
  return unwrapApiResponse(response.data)
}

export async function readNotification(notificationId: string) {
  const response = await protectedApi.put<ApiResponse<NotificationReadResult>>(
    `/api/v1/notifications/${notificationId}/read`,
  )
  return unwrapApiResponse(response.data)
}

export async function readAllNotifications(throughNotificationId: string) {
  const response = await protectedApi.put<ApiResponse<NotificationsReadAllResult>>(
    '/api/v1/notifications/read-all',
    { throughNotificationId },
  )
  return unwrapApiResponse(response.data)
}

import type { ApiResponse } from '@/features/auth/api/auth.types'
import { getAuthContext, isCurrentAuthContext, subscribeAuthContext, assertAuthContext, type AuthContext } from '@/features/auth/model/authContext'
import { getAuthPhase, subscribeAuthPhase } from '@/features/auth/model/authPhaseStore'
import type { QuizGenerationNotification } from '@/features/notification/api/notification.types'
import { notificationDestination } from '@/features/notification/model/notificationPresentation'
import { protectedApi } from '@/shared/api/protectedApi'
import { unwrapApiResponse, toApiClientError } from '@/shared/api/apiError'
import { queryClient } from '@/app/providers/queryClient'
import { parseNativePushMessage, isUuid } from './pushProtocol'
import type { NativeConnection, NativeSession } from './nativeBridge'
import { pushOpenStore, type PushOpenStore } from './pushOpenStore'

const LIFETIME = 90 * 24 * 60 * 60 * 1000
export function createPushOpenSession(connection: NativeConnection, navigate: (path: string) => Promise<void>, storage: PushOpenStore = pushOpenStore): NativeSession {
  let active = true
  let reading = false
  const pending = new Map<string, Extract<NonNullable<ReturnType<typeof parseNativePushMessage>>, { type: 'PUSH_OPEN' }>>()
  const inflight = new Set<string>()
  const current = (context: AuthContext) => active && getAuthPhase() === 'authenticated' && context.userId !== null && isCurrentAuthContext(context)
  const ack = (id: string, outcome: 'COMPLETED' | 'UNAVAILABLE', context: AuthContext) => {
    if (current(context)) { connection.send('PUSH_OPEN_ACK', { messageId: id, outcome, userId: context.userId }, context.authEpoch); pending.delete(id) }
  }
  async function get<T>(path: string, context: AuthContext): Promise<T> {
    assertAuthContext(context)
    const response = await protectedApi.get<ApiResponse<T>>(path, { authContext: context })
    assertAuthContext(context)
    return unwrapApiResponse(response.data)
  }
  async function open(message: NonNullable<ReturnType<typeof pending.get>>) {
    const context = getAuthContext()
    if (!current(context) || inflight.has(message.messageId)) return
    // Native sends only the selected binding's known owner; epochs reject stale deliveries.
    if (message.authEpoch !== context.authEpoch) { pending.delete(message.messageId); return }
    inflight.add(message.messageId)
    try {
      const key = `${context.userId}:${message.messageId}`
      const completed = await storage.get(key)
      if (!current(context)) return
      if (completed) { ack(message.messageId, completed.outcome, context); return }
      let notification: QuizGenerationNotification | undefined
      try { notification = await get<QuizGenerationNotification>(`/api/v1/notifications/${message.payload.notificationId}`, context) }
      catch (error) { if (toApiClientError(error).status !== 404) throw error }
      if (!current(context)) return
      let destination = '/notifications?unavailable=1'
      if (notification) {
        if (notification.notificationId !== message.payload.notificationId || !isUuid(notification.quizSetId) || !isUuid(notification.materialId)
          || !Number.isFinite(Date.parse(notification.createdAt)) || !['FOCUS_QUIZ_IN_LIST', 'RECONFIGURE_QUIZ'].includes(notification.actionType)) return
        if (notification.targetAvailable) {
          try {
            await get(notification.actionType === 'FOCUS_QUIZ_IN_LIST' ? `/api/v1/quiz-sets/${notification.quizSetId}` : `/api/v1/learning-materials/${notification.materialId}`, context)
            destination = notificationDestination(notification)
          } catch (error) { if (toApiClientError(error).status !== 404) throw error }
        }
      }
      if (!current(context)) return
      await navigate(destination)
      if (!current(context)) return
      const outcome = notification ? 'COMPLETED' as const : 'UNAVAILABLE' as const
      const expiresAt = notification ? Date.parse(notification.createdAt) + LIFETIME : Date.now() + LIFETIME
      if (expiresAt <= Date.now()) return
      await storage.complete({ key, userId: context.userId!, messageId: message.messageId, notificationId: message.payload.notificationId, outcome, expiresAt },
        notification ? { key: `${context.userId}:${notification.notificationId}`, userId: context.userId!, notificationId: notification.notificationId, expiresAt, nextAttemptAt: 0, attempts: 0 } : undefined)
      ack(message.messageId, outcome, context)
      void flushReads()
    } catch { /* No ACK on network, authentication, or durable storage failure. */ }
    finally { inflight.delete(message.messageId) }
  }
  async function flushReads() {
    const context = getAuthContext()
    if (!current(context) || reading) return
    reading = true
    try {
      for (const read of await storage.reads(context.userId!)) {
        if (!current(context)) return
        if (read.nextAttemptAt > Date.now()) continue
        try {
          assertAuthContext(context)
          await protectedApi.put(`/api/v1/notifications/${read.notificationId}/read`, undefined, { authContext: context })
          if (!current(context)) return
          await storage.update(read, true)
          if (current(context)) void queryClient.invalidateQueries({ queryKey: ['private', 'notifications'] })
        } catch (error) {
          if (!current(context)) return
          const failure = toApiClientError(error)
          const permanent = failure.status !== undefined && failure.status >= 400 && failure.status < 500 && failure.status !== 401 && failure.status !== 429
          await storage.update({ ...read, attempts: read.attempts + 1, nextAttemptAt: Date.now() + Math.max(Math.min(300000, 1000 * 2 ** Math.min(read.attempts, 8)), failure.retryAfterMs ?? 0) }, permanent)
        }
      }
    } catch { /* Retain queued reads when storage is unavailable. */ }
    finally { reading = false }
  }
  function resume() {
    for (const item of pending.values()) void open(item)
    void flushReads()
  }
  const offContext = subscribeAuthContext(resume)
  const offPhase = subscribeAuthPhase(resume)
  window.addEventListener('online', resume)
  document.addEventListener('visibilitychange', resume)
  const timer = window.setInterval(resume, 5000)
  queueMicrotask(resume)
  return {
    receive(raw) {
      const message = parseNativePushMessage(raw, connection.sessionId)
      if (!active || !message || message.type !== 'PUSH_OPEN' || pending.size >= 32) return
      pending.set(message.messageId, message)
      void open(message)
    },
    stop() { active = false; offContext(); offPhase(); window.clearInterval(timer); window.removeEventListener('online', resume); document.removeEventListener('visibilitychange', resume); pending.clear() },
  }
}

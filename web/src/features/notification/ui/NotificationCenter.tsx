import {
  IconBellLine,
  IconCheckmarkCircleFill,
  IconExclamationmarkCircleFill,
} from '@karrotmarket/react-monochrome-icon'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ActionButton,
  Icon,
  NotificationBadge,
  NotificationBadgePositioner,
  Snackbar,
  useSnackbarAdapter,
} from '@seed-design/react'
import { type ReactNode, useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { useCurrentUser } from '@/features/auth/model/auth.queries'
import { notificationsEnabled } from '@/features/notification/api/notificationAdapter'
import type { QuizGenerationNotification } from '@/features/notification/api/notification.types'
import {
  notificationActionLabel,
  notificationDestination,
  notificationMessage,
} from '@/features/notification/model/notificationPresentation'
import {
  claimSnackbarNotifications,
  clearPendingGeneration,
  loadPendingGeneration,
  subscribePendingGeneration,
} from '@/features/notification/model/notificationStorage'
import {
  notificationFirstPageQueryOptions,
  notificationKeys,
} from '@/features/notification/model/notificationQueries'
import { getQuizSet } from '@/features/quiz/api/quiz.api'
import { quizManagementKeys } from '@/features/quiz/model/quizManagementQueries'
import { quizQueryKeys } from '@/features/quiz/model/quizQueries'

export function NotificationCenterProvider({ children }: { children: ReactNode }) {
  return (
    <Snackbar.RootProvider pauseOnInteraction>
      <NotificationCenterRuntime />
      {children}
      <Snackbar.Region>
        <Snackbar.Renderer />
      </Snackbar.Region>
    </Snackbar.RootProvider>
  )
}

export function NotificationBell() {
  const navigate = useNavigate()
  const currentUser = useCurrentUser()
  const notifications = useQuery({
    ...notificationFirstPageQueryOptions(),
    enabled: notificationsEnabled && Boolean(currentUser.data),
  })
  const unreadCount = notifications.data?.unreadCount ?? 0
  const badgeLabel = unreadCount > 99 ? '99+' : String(unreadCount)

  return (
    <ActionButton
      className="app-notification-button"
      type="button"
      size="small"
      variant="ghost"
      layout="iconOnly"
      aria-label={unreadCount > 0 ? `알림, 읽지 않은 알림 ${unreadCount}개` : '알림'}
      onClick={() => navigate('/notifications')}
    >
      <Icon svg={<IconBellLine />} size="x5" />
      {unreadCount > 0 ? (
        <NotificationBadgePositioner attach="icon" size="large" aria-hidden>
          <NotificationBadge size="large">{badgeLabel}</NotificationBadge>
        </NotificationBadgePositioner>
      ) : null}
    </ActionButton>
  )
}

function NotificationCenterRuntime() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const snackbar = useSnackbarAdapter()
  const currentUser = useCurrentUser()
  const userId = currentUser.data?.id
  const [foreground, setForeground] = useState(() => document.visibilityState === 'visible' && navigator.onLine)
  const [pending, setPending] = useState(() => userId ? loadPendingGeneration(userId) : null)

  useEffect(() => {
    const syncForeground = () => setForeground(document.visibilityState === 'visible' && navigator.onLine)
    document.addEventListener('visibilitychange', syncForeground)
    window.addEventListener('online', syncForeground)
    window.addEventListener('offline', syncForeground)
    return () => {
      document.removeEventListener('visibilitychange', syncForeground)
      window.removeEventListener('online', syncForeground)
      window.removeEventListener('offline', syncForeground)
    }
  }, [])

  useEffect(() => {
    if (!userId) {
      setPending(null)
      return
    }
    const syncPending = () => setPending(loadPendingGeneration(userId))
    syncPending()
    return subscribePendingGeneration(syncPending)
  }, [userId])

  const pendingQuery = useQuery({
    queryKey: quizQueryKeys.quizSet(pending?.quizSetId ?? ''),
    queryFn: ({ signal }) => getQuizSet(pending!.quizSetId, signal),
    enabled: notificationsEnabled && Boolean(userId && pending && foreground),
    refetchInterval: (query) => {
      if (!foreground) return false
      const data = query.state.data
      return data?.status === 'GENERATING' ? data.pollAfterSeconds * 1_000 : false
    },
    refetchIntervalInBackground: false,
  })

  const notifications = useQuery({
    ...notificationFirstPageQueryOptions(),
    enabled: notificationsEnabled && Boolean(userId && foreground),
  })

  useEffect(() => {
    const state = pendingQuery.data
    if (!userId || !pending || !state || state.status === 'GENERATING') return
    clearPendingGeneration(userId, pending.quizSetId)
    void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    void queryClient.invalidateQueries({ queryKey: quizManagementKeys.all })
  }, [pending, pendingQuery.data, queryClient, userId])

  useEffect(() => {
    if (!userId || !foreground || !notifications.data) return
    const unseen = notifications.data.items.filter((item) => item.readAt === null)
    const claimedIds = claimSnackbarNotifications(userId, unseen.map((item) => item.notificationId))
    if (claimedIds.length === 0) return
    const claimed = unseen.filter((item) => claimedIds.includes(item.notificationId))
    if (isGenerationRoute(location.pathname)) return

    if (claimed.length > 1) {
      snackbar.create({
        render: () => (
          <AppSnackbar
            message={`새 퀴즈 알림이 ${claimed.length}개 있어요.`}
            actionLabel="알림보기"
            onAction={() => {
              snackbar.dismiss()
              navigate('/notifications')
            }}
          />
        ),
      })
      return
    }

    const notification = claimed[0]
    if (!notification) return
    const omitAction = location.pathname === '/learning/quizzes'
    snackbar.create({
      render: () => (
        <AppSnackbar
          notification={notification}
          message={notificationMessage(notification)}
          actionLabel={omitAction ? undefined : notificationActionLabel(notification)}
          onAction={omitAction ? undefined : () => {
            snackbar.dismiss()
            navigate(notificationDestination(notification))
          }}
        />
      ),
    })
  }, [foreground, location.pathname, navigate, notifications.data, snackbar, userId])

  return null
}

function AppSnackbar({ notification, message, actionLabel, onAction }: {
  notification?: QuizGenerationNotification
  message: string
  actionLabel?: string
  onAction?: () => void
}) {
  const variant = notification?.type === 'QUIZ_GENERATION_READY'
    ? 'positive'
    : notification?.type === 'QUIZ_GENERATION_FAILED'
      ? 'critical'
      : undefined
  const icon = notification?.type === 'QUIZ_GENERATION_READY'
    ? <IconCheckmarkCircleFill />
    : notification?.type === 'QUIZ_GENERATION_FAILED'
      ? <IconExclamationmarkCircleFill />
      : <IconBellLine />

  return (
    <Snackbar.Root variant={variant}>
      <Snackbar.Content>
        <Snackbar.PrefixIcon svg={icon} />
        <Snackbar.Message>{message}</Snackbar.Message>
      </Snackbar.Content>
      {actionLabel && onAction ? <Snackbar.ActionButton onClick={onAction}>{actionLabel}</Snackbar.ActionButton> : null}
      <Snackbar.HiddenCloseButton aria-label="알림 닫기" />
    </Snackbar.Root>
  )
}

function isGenerationRoute(pathname: string) {
  return pathname.startsWith('/quiz-sets/') || /^\/learning\/[^/]+\/quiz\/?$/.test(pathname)
}

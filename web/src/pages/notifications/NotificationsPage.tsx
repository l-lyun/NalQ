import { IconChevronRightLine } from '@karrotmarket/react-monochrome-icon'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ActionButton, Box, Divider, Flex, Icon, List, Skeleton, Text, VStack } from '@seed-design/react'
import { Fragment, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import {
  listManagedNotifications,
  notificationsEnabled,
  readAllManagedNotifications,
  readManagedNotification,
} from '@/features/notification/api/notificationAdapter'
import type { QuizGenerationNotification } from '@/features/notification/api/notification.types'
import {
  formatNotificationTime,
  notificationDestination,
  notificationMessage,
} from '@/features/notification/model/notificationPresentation'
import { notificationKeys } from '@/features/notification/model/notificationQueries'
import { LearningScreenHeader } from '@/pages/learning/components/LearningPrimitives'

import './notifications.css'

export function NotificationsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState<string>()
  const notifications = useInfiniteQuery({
    queryKey: notificationKeys.list,
    queryFn: ({ pageParam, signal }) => listManagedNotifications(pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor ?? undefined : undefined,
    enabled: notificationsEnabled,
  })
  const items = notifications.data?.pages.flatMap((page) => page.items) ?? []
  const unreadCount = notifications.data?.pages[0]?.unreadCount ?? 0
  const newestId = items[0]?.notificationId
  const refresh = () => void queryClient.invalidateQueries({ queryKey: notificationKeys.all })
  const readOne = useMutation({ mutationFn: readManagedNotification, onSettled: refresh })
  const readAll = useMutation({ mutationFn: readAllManagedNotifications, onSettled: refresh })

  const selectNotification = (notification: QuizGenerationNotification) => {
    readOne.mutate(notification.notificationId)
    if (!notification.targetAvailable) {
      setNotice('대상을 찾을 수 없어요.')
      return
    }
    navigate(notificationDestination(notification))
  }

  return (
    <Box as="main" className="notifications-page" bg="bg.layerDefault" minHeight="100dvh" pt="safeArea">
      <VStack className="notifications-content" px="spacingX.globalGutter" pt="x4" pb="spacingY.screenBottom" gap="x4">
        <Flex align="center" justify="space-between" gap="x2">
          <LearningScreenHeader
            title="알림"
            onBack={() => location.key === 'default' ? navigate('/', { replace: true }) : navigate(-1)}
          />
          {unreadCount > 0 && newestId ? (
            <ActionButton
              type="button"
              size="small"
              variant="ghost"
              color="fg.brand"
              disabled={readAll.isPending}
              onClick={() => readAll.mutate(newestId)}
            >
              모두 읽음
            </ActionButton>
          ) : null}
        </Flex>
        {notice ? <Text role="status" textStyle="t4Regular" color="fg.neutralMuted">{notice}</Text> : null}
        {readAll.isError ? (
          <InlineError message="모두 읽음으로 표시하지 못했어요." onRetry={() => newestId && readAll.mutate(newestId)} />
        ) : null}
        {notifications.isPending ? (
          <NotificationSkeleton />
        ) : notifications.isError && items.length === 0 ? (
          <CenteredState message="알림을 불러오지 못했어요." actionLabel="다시 시도" onAction={() => void notifications.refetch()} />
        ) : items.length === 0 ? (
          <CenteredState message="아직 알림이 없어요." />
        ) : (
          <List.Root className="notifications-list" aria-label="알림 목록" width="full" itemBorderRadius="r2_5">
            {items.map((notification, index) => (
              <Fragment key={notification.notificationId}>
                <List.Item alignItems="flex-start">
                  <List.Prefix>
                    <span className="notifications-unread-marker" data-unread={notification.readAt ? undefined : ''} aria-hidden />
                  </List.Prefix>
                  <List.Content asChild gap="x2">
                    <button className="notifications-item-button" type="button" onClick={() => selectNotification(notification)}>
                      <VStack minWidth="0px" flexGrow gap="x1" align="flex-start">
                        {!notification.readAt ? <span className="notifications-visually-hidden">읽지 않음</span> : null}
                        <List.Title className="notifications-message" data-unread={notification.readAt ? undefined : ''}>
                          {notificationMessage(notification)}
                        </List.Title>
                        <Text className="notifications-target-name" textStyle="t4Regular" color="fg.neutralMuted">{notification.targetName}</Text>
                        <List.Detail>{formatNotificationTime(notification.createdAt)}</List.Detail>
                      </VStack>
                      <List.Suffix><Icon svg={<IconChevronRightLine />} size="x4_5" /></List.Suffix>
                    </button>
                  </List.Content>
                </List.Item>
                {index < items.length - 1 ? <Divider as="li" aria-hidden color="stroke.neutralSubtle" inset /> : null}
              </Fragment>
            ))}
          </List.Root>
        )}
        {notifications.isFetchingNextPage && items.length > 0 ? <Text role="status" textStyle="t4Regular" color="fg.neutralMuted">알림을 더 불러오고 있어요.</Text> : null}
        {notifications.isFetchNextPageError ? <InlineError message="알림을 더 불러오지 못했어요." onRetry={() => void notifications.fetchNextPage()} /> : null}
        {notifications.hasNextPage ? (
          <ActionButton type="button" size="medium" variant="neutralWeak" disabled={notifications.isFetchingNextPage} onClick={() => void notifications.fetchNextPage()}>
            더보기
          </ActionButton>
        ) : null}
      </VStack>
    </Box>
  )
}

function NotificationSkeleton() {
  return <VStack gap="x4" aria-label="알림을 불러오는 중" aria-busy="true">{[1, 2, 3].map((key) => <VStack key={key} gap="x2"><Skeleton width="70%" height="x5" radius="8" /><Skeleton width="90%" height="x4" radius="8" /><Skeleton width="25%" height="x3" radius="8" /></VStack>)}</VStack>
}

function CenteredState({ message, actionLabel, onAction }: { message: string; actionLabel?: string; onAction?: () => void }) {
  return <VStack className="notifications-centered-state" minHeight="320px" align="center" justify="center" gap="x4"><Text textStyle="t5Regular" color="fg.neutralMuted" align="center">{message}</Text>{actionLabel && onAction ? <ActionButton type="button" size="medium" variant="neutralWeak" onClick={onAction}>{actionLabel}</ActionButton> : null}</VStack>
}

function InlineError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <Flex align="center" justify="space-between" gap="x3"><Text role="alert" textStyle="t4Regular" color="fg.neutralMuted">{message}</Text><ActionButton type="button" size="small" variant="ghost" onClick={onRetry}>다시 시도</ActionButton></Flex>
}

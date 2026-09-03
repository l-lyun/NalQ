import { queryOptions } from '@tanstack/react-query'

import { listManagedNotifications } from '../api/notificationAdapter'

export const notificationKeys = {
  all: ['private', 'notifications'] as const,
  firstPage: ['private', 'notifications', 'first-page'] as const,
  list: ['private', 'notifications', 'list'] as const,
}

export const notificationFirstPageQueryOptions = () => queryOptions({
  queryKey: notificationKeys.firstPage,
  queryFn: ({ signal }) => listManagedNotifications(undefined, signal),
  staleTime: 0,
  refetchOnWindowFocus: 'always',
  refetchOnReconnect: 'always',
  refetchInterval: false,
})

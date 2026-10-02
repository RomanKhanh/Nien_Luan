import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationApi } from '@/api/endpoints'
import type { NotificationType } from '@/api/types'

export const notificationKeys = {
  all: ['notifications'] as const,
  unread: ['notifications', 'unread'] as const,
  list: (page: number, size: number) => ['notifications', 'list', page, size] as const,
}

// số chưa đọc: hỏi lại mỗi 30 giây khi tab đang mở, và khi quay lại tab (mặc định của react-query)
export function useUnreadCount(enabled = true) {
  return useQuery({
    queryKey: notificationKeys.unread,
    queryFn: notificationApi.unreadCount,
    enabled,
    refetchInterval: 30_000,
  })
}

export function useNotificationList(page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: notificationKeys.list(page, size),
    queryFn: () => notificationApi.list(page, size),
    enabled,
  })
}

export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationApi.markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}

// types rỗng = tất cả (admin mở một mục thì chỉ đánh dấu loại của mục đó)
export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (types: NotificationType[] = []) => notificationApi.markAllRead(types),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}

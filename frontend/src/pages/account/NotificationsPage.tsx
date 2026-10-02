import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { NotificationItem } from '@/features/notifications/NotificationItem'
import { useMarkAllRead, useNotificationList, useUnreadCount } from '@/features/notifications/useNotifications'
import { AccountShell } from './AccountShell'

const PAGE_SIZE = 15

export default function NotificationsPage() {
  const [page, setPage] = useState(0)
  const list = useNotificationList(page, PAGE_SIZE)
  const unread = useUnreadCount()
  const markAllRead = useMarkAllRead()

  return (
    <AccountShell>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="h1">Thông báo</h1>
        {(unread.data?.total ?? 0) > 0 && (
          <Button variant="secondary" size="sm" loading={markAllRead.isPending} onClick={() => markAllRead.mutate([])}>
            Đánh dấu đã đọc tất cả
          </Button>
        )}
      </div>
      <div className="card mt-5 p-2">
        {list.isPending ? (
          <PageLoader />
        ) : list.isError ? (
          <ErrorState message={list.error.message} onRetry={() => list.refetch()} />
        ) : list.data.content.length === 0 ? (
          <EmptyState
            title="Chưa có thông báo nào"
            description="Khi đơn hàng, hồ sơ kỹ năng của bé hoặc yêu cầu của bạn có cập nhật, thông báo sẽ hiện ở đây."
          />
        ) : (
          <ul className="space-y-0.5">
            {list.data.content.map((n) => (
              <li key={n.id}>
                <NotificationItem item={n} />
              </li>
            ))}
          </ul>
        )}
      </div>
      {list.data && (
        <div className="mt-4">
          <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
        </div>
      )}
    </AccountShell>
  )
}

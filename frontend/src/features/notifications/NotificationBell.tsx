import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Skeleton } from '@/components/ui/States'
import { NotificationItem } from './NotificationItem'
import { useMarkAllRead, useNotificationList, useUnreadCount } from './useNotifications'

const PREVIEW_SIZE = 8

// chuông ở header khách hàng: số chưa đọc + danh sách gần nhất khi bấm mở
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const unread = useUnreadCount()
  const list = useNotificationList(0, PREVIEW_SIZE, open)
  const markAllRead = useMarkAllRead()
  const count = unread.data?.total ?? 0

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'}
        className="relative grid h-10 w-10 place-items-center rounded-full text-ink hover:bg-muted"
      >
        <BellIcon />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 grid h-5 min-w-5 place-items-center rounded-full border-2 border-surface bg-coral px-1 text-[10.5px] font-bold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="Thông báo"
          className="fixed inset-x-3 top-[7.5rem] z-50 rounded-2xl border border-line bg-surface p-2 shadow-pop sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-[360px]"
        >
          <div className="flex items-center justify-between px-2 pb-2 pt-1">
            <p className="font-display text-[17px] font-extrabold">Thông báo</p>
            {count > 0 && (
              <button
                type="button"
                className="text-[12.5px] font-semibold text-primary disabled:opacity-50"
                disabled={markAllRead.isPending}
                onClick={() => markAllRead.mutate([])}
              >
                Đánh dấu đã đọc tất cả
              </button>
            )}
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {list.isPending ? (
              <div className="space-y-2 p-2">
                <Skeleton className="h-14" />
                <Skeleton className="h-14" />
              </div>
            ) : list.isError ? (
              <p className="px-3 py-6 text-center text-[13px] text-ink-muted">Không tải được thông báo.</p>
            ) : list.data.content.length === 0 ? (
              <p className="px-3 py-8 text-center text-[13.5px] text-ink-muted">
                Chưa có thông báo nào. Khi đơn hàng hoặc yêu cầu của bạn có cập nhật, Bin sẽ báo ở đây.
              </p>
            ) : (
              <ul className="space-y-0.5">
                {list.data.content.map((n) => (
                  <li key={n.id}>
                    <NotificationItem item={n} onOpen={() => setOpen(false)} />
                  </li>
                ))}
              </ul>
            )}
          </div>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="mt-1 block rounded-xl px-3 py-2 text-center text-[13px] font-semibold hover:bg-muted"
          >
            Xem tất cả
          </Link>
        </div>
      )}
    </div>
  )
}

function BellIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-[22px] w-[22px]"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden
    >
      <path d="M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15L6 16Z" strokeLinejoin="round" />
      <path d="M10 20.5a2 2 0 0 0 4 0" strokeLinecap="round" />
    </svg>
  )
}

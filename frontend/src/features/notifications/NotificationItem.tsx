import { useNavigate } from 'react-router-dom'
import type { AppNotification, NotificationType } from '@/api/types'
import { formatRelative } from '@/lib/format'
import { useMarkRead } from './useNotifications'

const ICONS: Record<NotificationType, string> = {
  ORDER_STATUS: '📦',
  SKILL_PROFILE_UPDATED: '🌱',
  COMPLAINT_UPDATED: '💬',
  NEW_ORDER: '🛒',
  ORDER_CANCELLED_BY_CUSTOMER: '↩️',
  NEW_COMPLAINT: '💬',
  NEW_REVIEW: '⭐',
}

// một dòng thông báo: bấm vào thì đánh dấu đã đọc rồi mở trang liên quan
export function NotificationItem({ item, onOpen }: { item: AppNotification; onOpen?: () => void }) {
  const navigate = useNavigate()
  const markRead = useMarkRead()
  const open = () => {
    if (!item.read) markRead.mutate(item.id)
    onOpen?.()
    if (item.link) navigate(item.link)
  }
  return (
    <button
      type="button"
      onClick={open}
      className={`flex w-full gap-3 rounded-xl px-3 py-2.5 text-left transition hover:bg-sky-soft ${item.read ? '' : 'bg-primary-soft/60'}`}
    >
      <span
        className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-surface text-[17px] shadow-card"
        aria-hidden
      >
        {ICONS[item.type] ?? '🔔'}
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={`block text-[13.5px] leading-snug ${item.read ? 'font-semibold text-ink-2' : 'font-bold text-ink'}`}
        >
          {item.title}
        </span>
        {item.message && <span className="mt-0.5 line-clamp-2 block text-[12.5px] text-ink-muted">{item.message}</span>}
        <span className="mt-0.5 block text-[11.5px] text-ink-faint">{formatRelative(item.createdAt)}</span>
      </span>
      {!item.read && <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-primary" aria-label="Chưa đọc" />}
    </button>
  )
}

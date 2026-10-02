import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import type { NotificationType } from '@/api/types'
import { useAuth } from '@/auth/AuthContext'
import { useMarkAllRead, useUnreadCount } from '@/features/notifications/useNotifications'
import { Logo } from '@/components/layout/Logo'
import { ScrollToTop } from '@/components/layout/ScrollToTop'
import { initials } from '@/lib/format'

// badge: loại thông báo đếm vào số mới của mục đó; mở mục thì các thông báo này được đánh dấu đã đọc.
// dataKey: query của trang đó, tải lại khi có mục mới trong lúc admin đang mở trang
const NAV: {
  to: string
  label: string
  icon: string
  end?: boolean
  badge?: NotificationType[]
  dataKey?: string[]
}[] = [
  { to: '/admin', label: 'Tổng quan', icon: '📊', end: true },
  { to: '/admin/products', label: 'Sản phẩm & kho', icon: '🧸' },
  { to: '/admin/catalog', label: 'Danh mục & kỹ năng', icon: '🗂️' },
  {
    to: '/admin/orders',
    label: 'Đơn hàng',
    icon: '📦',
    badge: ['NEW_ORDER', 'ORDER_CANCELLED_BY_CUSTOMER'],
    dataKey: ['admin', 'orders'],
  },
  {
    to: '/admin/complaints',
    label: 'Khiếu nại & yêu cầu',
    icon: '💬',
    badge: ['NEW_COMPLAINT'],
    dataKey: ['admin', 'complaints'],
  },
  { to: '/admin/reviews', label: 'Đánh giá', icon: '⭐', badge: ['NEW_REVIEW'], dataKey: ['admin', 'reviews'] },
  { to: '/admin/users', label: 'Người dùng', icon: '👥' },
  { to: '/admin/chatbot', label: 'Chatbot AI', icon: '🤖' },
  { to: '/admin/knowledge', label: 'Cơ sở tri thức', icon: '📚' },
]

export default function AdminLayout() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })
  const queryClient = useQueryClient()
  const unread = useUnreadCount()
  const markAllRead = useMarkAllRead()
  const { mutate: markSectionRead } = markAllRead
  const countFor = (types?: NotificationType[]) =>
    (types ?? []).reduce((sum, t) => sum + (unread.data?.byType[t] ?? 0), 0)

  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  // vào mục Đơn hàng / Khiếu nại / Đánh giá: coi như đã xem các mục mới của mục đó
  const current = NAV.find((item) => item.badge && location.pathname.startsWith(item.to))
  const currentNew = countFor(current?.badge)
  useEffect(() => {
    if (!current?.badge || currentNew === 0) return
    markSectionRead(current.badge)
    queryClient.invalidateQueries({ queryKey: current.dataKey })
  }, [current, currentNew, markSectionRead, queryClient])

  return (
    <div className="min-h-screen bg-[#f3f8fb] lg:grid lg:grid-cols-[240px_1fr]">
      <aside className="border-b border-line bg-surface lg:sticky lg:top-0 lg:h-screen lg:border-b-0 lg:border-r">
        <div className="flex items-center justify-between px-4 py-4 lg:px-5 lg:py-5">
          <Logo to="/admin" suffix="Admin" />
          <Link to="/" className="text-[13px] font-semibold lg:hidden">
            Cửa hàng ↗
          </Link>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:px-3" aria-label="Quản trị">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex shrink-0 items-center gap-2.5 rounded-full px-3.5 py-2 text-[14px] font-semibold transition ${
                  isActive
                    ? 'bg-primary text-white shadow-[0_3px_0_#3a22b8] hover:text-white'
                    : 'text-ink-2 hover:bg-sky-soft hover:text-ink'
                }`
              }
            >
              <span aria-hidden>{item.icon}</span>
              {item.label}
              {countFor(item.badge) > 0 && (
                <span
                  className="ml-auto grid h-5 min-w-5 place-items-center rounded-full bg-coral px-1.5 text-[11px] font-bold text-white"
                  aria-label={`${countFor(item.badge)} mới`}
                >
                  {countFor(item.badge)}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="hidden border-t border-line p-4 lg:absolute lg:inset-x-0 lg:bottom-0 lg:block">
          <div className="flex items-center gap-2.5">
            <span className="grid h-10 w-10 place-items-center rounded-full border-[3px] border-sun bg-primary font-display text-[14px] font-extrabold text-white">
              {me.data ? initials(me.data.fullName) : '…'}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[13.5px] font-semibold">{me.data?.fullName}</p>
              <p className="truncate text-[12px] text-ink-muted">{me.data?.email}</p>
            </div>
          </div>
          <div className="mt-3 flex gap-2 text-[13px] font-semibold">
            <Link to="/">Xem cửa hàng</Link>
            <span className="text-line-strong">·</span>
            <button
              type="button"
              className="text-danger"
              onClick={() => {
                logout()
                navigate('/login')
              }}
            >
              Đăng xuất
            </button>
          </div>
        </div>
      </aside>
      <main className="min-w-0 px-4 py-6 sm:px-6 lg:px-8">
        <Outlet />
        <ScrollToTop />
      </main>
    </div>
  )
}

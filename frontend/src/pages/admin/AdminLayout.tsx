import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Logo } from '@/components/layout/Logo'
import { ScrollToTop } from '@/components/layout/ScrollToTop'
import { initials } from '@/lib/format'

const NAV = [
  { to: '/admin', label: 'Tổng quan', icon: '📊', end: true },
  { to: '/admin/products', label: 'Sản phẩm & kho', icon: '🧸' },
  { to: '/admin/catalog', label: 'Danh mục & kỹ năng', icon: '🗂️' },
  { to: '/admin/orders', label: 'Đơn hàng', icon: '📦' },
  { to: '/admin/complaints', label: 'Khiếu nại & yêu cầu', icon: '💬' },
  { to: '/admin/reviews', label: 'Đánh giá', icon: '⭐' },
  { to: '/admin/users', label: 'Người dùng', icon: '👥' },
  { to: '/admin/chatbot', label: 'Chatbot AI', icon: '🤖' },
  { to: '/admin/knowledge', label: 'Cơ sở tri thức', icon: '📚' },
]

export default function AdminLayout() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })

  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

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

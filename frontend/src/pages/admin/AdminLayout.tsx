import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Logo } from '@/components/layout/Logo'
import { initials } from '@/lib/format'

const NAV = [
  { to: '/admin', label: 'Tổng quan', end: true },
  { to: '/admin/products', label: 'Sản phẩm & kho' },
  { to: '/admin/catalog', label: 'Danh mục & kỹ năng' },
  { to: '/admin/orders', label: 'Đơn hàng' },
  { to: '/admin/complaints', label: 'Khiếu nại & yêu cầu' },
  { to: '/admin/reviews', label: 'Đánh giá' },
  { to: '/admin/users', label: 'Người dùng' },
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
    <div className="min-h-screen bg-[#F4F2EE] lg:grid lg:grid-cols-[232px_1fr]">
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
                `shrink-0 rounded-md px-3 py-2 text-[14px] font-medium ${
                  isActive ? 'bg-ink text-white hover:text-white' : 'text-ink-2 hover:bg-muted hover:text-ink'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden border-t border-line p-4 lg:absolute lg:inset-x-0 lg:bottom-0 lg:block">
          <div className="flex items-center gap-2.5">
            <span className="grid h-9 w-9 place-items-center rounded-full bg-secondary text-[12px] font-bold text-white">
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
      </main>
    </div>
  )
}

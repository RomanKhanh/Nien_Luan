import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { initials } from '@/lib/format'

// khung trang tài khoản với menu bên trái (mockup màn 08); mobile: menu thành hàng tab cuộn ngang
export function AccountShell({ children }: { children: ReactNode }) {
  const { isCustomer, logout } = useAuth()
  const navigate = useNavigate()
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })
  const links = [
    { to: '/account', label: 'Thông tin tài khoản' },
    ...(isCustomer
      ? [
          { to: '/children', label: 'Hồ sơ bé' },
          { to: '/orders', label: 'Đơn hàng của tôi' },
          { to: '/feedback', label: 'Phản hồi & khiếu nại' },
        ]
      : []),
  ]
  return (
    <div className="container-page grid gap-8 py-8 lg:grid-cols-[240px_1fr]">
      <aside>
        <div className="card p-4 lg:sticky lg:top-20">
          <div className="flex items-center gap-3 border-b border-line pb-4">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-secondary text-[14px] font-bold text-white">
              {me.data ? initials(me.data.fullName) : '…'}
            </span>
            <div className="min-w-0">
              <p className="truncate font-bold">{me.data?.fullName ?? '…'}</p>
              {me.data && (
                <p className="text-[12.5px] text-ink-muted">
                  Thành viên từ {new Date(me.data.createdAt).getFullYear()}
                </p>
              )}
            </div>
          </div>
          <nav className="-mx-1 mt-3 flex gap-1 overflow-x-auto lg:flex-col" aria-label="Tài khoản">
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                end
                className={({ isActive }) =>
                  `shrink-0 rounded-md px-3 py-2 text-[14px] font-medium ${
                    isActive ? 'bg-primary-soft text-primary-hover' : 'text-ink-2 hover:bg-muted hover:text-ink'
                  }`
                }
              >
                {l.label}
              </NavLink>
            ))}
            <button
              type="button"
              className="shrink-0 rounded-md px-3 py-2 text-left text-[14px] font-medium text-danger hover:bg-danger-soft"
              onClick={() => {
                logout()
                navigate('/')
              }}
            >
              Đăng xuất
            </button>
          </nav>
        </div>
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { Bubbles, Mascot, TornEdge } from '@/components/decor/Decor'
import { initials } from '@/lib/format'

// khung trang tài khoản với menu bên trái (mockup màn 08); mobile: menu thành hàng tab cuộn ngang
export function AccountShell({ children }: { children: ReactNode }) {
  const { isCustomer, logout } = useAuth()
  const navigate = useNavigate()
  const me = useQuery({ queryKey: ['me'], queryFn: meApi.get })
  const links = [
    { to: '/account', label: 'Thông tin tài khoản', icon: '👤' },
    ...(isCustomer
      ? [
          { to: '/children', label: 'Hồ sơ bé', icon: '🧒' },
          { to: '/orders', label: 'Đơn hàng của tôi', icon: '📦' },
          { to: '/notifications', label: 'Thông báo', icon: '🔔' },
          { to: '/feedback', label: 'Phản hồi & khiếu nại', icon: '💬' },
        ]
      : []),
  ]
  return (
    <>
      {/* dải chào nhỏ phía trên, cùng phong cách trời xanh với các trang khác */}
      <section className="relative overflow-hidden bg-gradient-to-br from-sky to-sky-deep text-white">
        <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
        <Bubbles />
        <div className="container-page relative flex items-center gap-4 pb-12 pt-6">
          <Mascot className="w-16 shrink-0 animate-float sm:w-20" />
          <div className="min-w-0">
            <p className="inline-flex rounded-full bg-sun px-3 py-0.5 text-[12.5px] font-bold uppercase tracking-[0.06em] text-ink">
              Tài khoản của tôi
            </p>
            <p className="sticker-text-sm truncate font-display text-[26px] font-extrabold leading-tight sm:text-[32px]">
              Xin chào, {me.data?.fullName ?? '…'} 👋
            </p>
          </div>
        </div>
        <TornEdge className="absolute inset-x-0 bottom-0" seed={5} />
      </section>
      <div className="container-page grid grid-cols-1 gap-8 py-8 lg:grid-cols-[240px_minmax(0,1fr)]">
        <aside>
          <div className="card p-4 lg:sticky lg:top-28">
            <div className="flex items-center gap-3 border-b border-line pb-4">
              <span className="grid h-12 w-12 place-items-center rounded-full border-[3px] border-sun bg-primary font-display text-[17px] font-extrabold text-white">
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
                    `flex shrink-0 items-center gap-2 rounded-full px-3.5 py-2 text-[14px] font-semibold transition ${
                      isActive
                        ? 'bg-primary text-white shadow-[0_3px_0_#3a22b8]'
                        : 'text-ink-2 hover:bg-sky-soft hover:text-ink'
                    }`
                  }
                >
                  <span aria-hidden>{l.icon}</span>
                  {l.label}
                </NavLink>
              ))}
              <button
                type="button"
                className="flex shrink-0 items-center gap-2 rounded-full px-3.5 py-2 text-left text-[14px] font-semibold text-danger hover:bg-danger-soft"
                onClick={() => {
                  logout()
                  navigate('/')
                }}
              >
                <span aria-hidden>🚪</span>
                Đăng xuất
              </button>
            </nav>
          </div>
        </aside>
        <div className="min-w-0">{children}</div>
      </div>
    </>
  )
}

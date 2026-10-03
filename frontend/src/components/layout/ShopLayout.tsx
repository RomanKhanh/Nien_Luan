import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/endpoints'
import { useAuth } from '@/auth/AuthContext'
import { buttonClass } from '@/components/ui/Button'
import { useCartCount } from '@/features/cart/useCart'
import { NotificationBell } from '@/features/notifications/NotificationBell'
import { SearchBox } from '@/features/catalog/SearchBox'
import { initials } from '@/lib/format'
import { Bubbles, Mascot, TornEdge } from '@/components/decor/Decor'
import { Logo } from './Logo'
import { ChatWidget } from '@/features/chat/ChatWidget'
import { ScrollToTop } from './ScrollToTop'

const NAV = [
  { to: '/', label: 'Trang chủ', end: true },
  { to: '/products', label: 'Sản phẩm', end: false },
  { to: '/children', label: 'Hồ sơ bé', end: false },
  { to: '/chat', label: 'Tư vấn AI', end: false },
]

export function ShopLayout() {
  const location = useLocation()
  const { isAdmin } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  // đổi trang thì đóng menu mobile (so với trang lúc mở menu) và cuộn lên đầu
  const [menuPath, setMenuPath] = useState(location.pathname)
  if (menuPath !== location.pathname) {
    setMenuPath(location.pathname)
    setMenuOpen(false)
  }
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  return (
    <div className="flex min-h-screen flex-col">
      <Header menuOpen={menuOpen} onToggleMenu={() => setMenuOpen((v) => !v)} />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
      {/* nút chat Bin chiếm góc dưới phải (trừ trang /chat và tài khoản admin) */}
      <ScrollToTop raised={!isAdmin && !location.pathname.startsWith('/chat')} />
      <ChatWidget />
    </div>
  )
}

function Header({ menuOpen, onToggleMenu }: { menuOpen: boolean; onToggleMenu: () => void }) {
  const { session, isCustomer, isAdmin } = useAuth()
  const cartCount = useCartCount()

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-surface/95 backdrop-blur">
      {/* dải thông báo nhiều màu phía trên header */}
      <div className="bg-gradient-to-r from-sky via-primary to-[#e8467c] text-white">
        <p className="container-page truncate py-1.5 text-center text-[12.5px] font-semibold">
          🎁 Học mà chơi, rời xa màn hình — chọn đồ chơi STEM theo lộ trình kỹ năng của bé
        </p>
      </div>
      <div className="container-page flex h-16 items-center gap-4">
        <button
          type="button"
          className="grid h-9 w-9 place-items-center rounded-md text-[18px] lg:hidden"
          onClick={onToggleMenu}
          aria-label="Mở menu"
          aria-expanded={menuOpen}
        >
          ☰
        </button>
        <Logo />
        <nav className="ml-4 hidden gap-1 text-[14px] font-semibold lg:flex" aria-label="Điều hướng chính">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `rounded-full px-3.5 py-1.5 transition ${isActive ? 'bg-primary-soft text-primary' : 'text-ink-2 hover:bg-muted hover:text-ink'}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <SearchBox className="ml-auto hidden max-w-xs flex-1 md:block" />
        <div className="ml-auto flex items-center gap-2 md:ml-0">
          {isAdmin && (
            <Link to="/admin" className={buttonClass('soft', 'sm')}>
              Trang quản trị
            </Link>
          )}
          {(isCustomer || !session) && (
            <Link
              to="/cart"
              className="relative flex items-center gap-2 rounded-md px-2.5 py-2 text-[14px] font-semibold text-ink hover:bg-muted hover:text-ink"
              aria-label={`Giỏ hàng, ${cartCount} sản phẩm`}
            >
              <CartIcon />
              <span className="hidden sm:inline">Giỏ hàng</span>
              {cartCount > 0 && (
                <span className="grid h-5 min-w-5 place-items-center rounded-full bg-primary px-1 text-[11px] font-bold text-white">
                  {cartCount}
                </span>
              )}
            </Link>
          )}
          {isCustomer && <NotificationBell />}
          {session ? (
            <UserMenu />
          ) : (
            <Link to="/login" className={buttonClass('primary', 'sm')}>
              Đăng nhập
            </Link>
          )}
        </div>
      </div>
      {menuOpen && (
        <div className="border-t border-line bg-surface lg:hidden">
          <div className="container-page flex flex-col gap-1 py-3">
            <SearchBox className="mb-2 md:hidden" />
            {NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `rounded-md px-3 py-2.5 text-[15px] font-medium ${isActive ? 'bg-primary-soft text-primary' : 'text-ink-2'}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </div>
        </div>
      )}
    </header>
  )
}

function UserMenu() {
  const { isAdmin, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const { data: me } = useQuery({ queryKey: ['me'], queryFn: meApi.get, staleTime: 5 * 60_000 })

  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    return () => document.removeEventListener('mousedown', onClick)
  }, [open])

  const items = isAdmin
    ? [
        { to: '/admin', label: 'Trang quản trị' },
        { to: '/account', label: 'Tài khoản' },
      ]
    : [
        { to: '/account', label: 'Thông tin tài khoản' },
        { to: '/children', label: 'Hồ sơ bé' },
        { to: '/orders', label: 'Đơn hàng của tôi' },
        { to: '/notifications', label: 'Thông báo' },
        { to: '/feedback', label: 'Phản hồi & khiếu nại' },
      ]

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-2 rounded-full py-1 pl-1 pr-2.5 hover:bg-muted"
      >
        <span className="grid h-9 w-9 place-items-center rounded-full border-[3px] border-sun bg-primary font-display text-[13px] font-extrabold text-white">
          {me ? initials(me.fullName) : '…'}
        </span>
        <span className="hidden max-w-[120px] truncate text-[14px] font-semibold xl:inline">{me?.fullName}</span>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 top-full mt-2 w-56 rounded-md border border-line bg-surface p-1.5 shadow-pop"
        >
          {items.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              role="menuitem"
              onClick={() => setOpen(false)}
              className="block rounded-sm px-3 py-2 text-[14px] text-ink-2 hover:bg-muted hover:text-ink"
            >
              {item.label}
            </Link>
          ))}
          <button
            type="button"
            role="menuitem"
            className="block w-full rounded-sm px-3 py-2 text-left text-[14px] text-danger hover:bg-danger-soft"
            onClick={() => {
              logout()
              navigate('/')
            }}
          >
            Đăng xuất
          </button>
        </div>
      )}
    </div>
  )
}

function Footer() {
  return (
    <footer className="mt-20">
      <TornEdge color="var(--color-secondary)" seed={11} className="-mb-px" />
      <div className="relative overflow-hidden bg-secondary text-[#BFC7DA]">
        <div className="opacity-30">
          <Bubbles />
        </div>
        <div className="container-page relative grid gap-8 py-10 text-[14px] sm:grid-cols-3">
          <div className="flex items-start gap-3">
            <Mascot className="w-16 shrink-0 animate-float" />
            <div>
              <p className="font-display text-[22px] font-extrabold leading-tight text-white">BrainBlocks</p>
              <p className="mt-1">Đồ chơi giáo dục STEM, chọn theo lộ trình kỹ năng của bé.</p>
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <p className="font-semibold text-white">Khám phá</p>
            <Link to="/products" className="text-[#BFC7DA] hover:text-white">
              Tất cả sản phẩm
            </Link>
            <Link to="/children" className="text-[#BFC7DA] hover:text-white">
              Hồ sơ & lộ trình kỹ năng
            </Link>
          </div>
          <div className="flex flex-col gap-1.5">
            <p className="font-semibold text-white">Hỗ trợ</p>
            <Link to="/orders" className="text-[#BFC7DA] hover:text-white">
              Theo dõi đơn hàng
            </Link>
            <Link to="/feedback" className="text-[#BFC7DA] hover:text-white">
              Phản hồi & khiếu nại
            </Link>
          </div>
        </div>
        <p className="container-page relative border-t border-white/10 py-4 text-[12.5px]">
          Chỉ số kỹ năng chỉ mang tính tham khảo khi chọn đồ chơi, không đánh giá năng lực hay sự phát triển của trẻ.
        </p>
      </div>
    </footer>
  )
}

function CartIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
      <path
        d="M3 4h2l2.2 10.2a1.5 1.5 0 0 0 1.5 1.2h8.6a1.5 1.5 0 0 0 1.5-1.1L21 8H6.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="9.5" cy="19.5" r="1.3" />
      <circle cx="17" cy="19.5" r="1.3" />
    </svg>
  )
}

import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Bubbles, TornEdge } from '@/components/decor/Decor'

// dải trời xanh đầu trang (bong bóng + mép giấy xé), dùng chung cho các trang phía khách hàng
export function PageHero({
  title,
  subtitle,
  crumbs,
  kicker,
  actions,
  children,
}: {
  title: ReactNode
  subtitle?: ReactNode
  // đường dẫn: phần tử cuối là trang hiện tại (không có link)
  crumbs?: { label: string; to?: string }[]
  kicker?: ReactNode
  actions?: ReactNode
  children?: ReactNode
}) {
  return (
    <section className="relative overflow-hidden bg-gradient-to-br from-sky to-sky-deep text-white">
      <div className="dots-bg absolute inset-0 opacity-50" aria-hidden />
      <Bubbles />
      <div className="container-page relative pb-12 pt-6 sm:pb-14 sm:pt-8">
        {crumbs && (
          <nav className="flex flex-wrap items-center gap-1.5 text-[13px] text-white/80" aria-label="Đường dẫn">
            <Link to="/" className="text-white/80 hover:text-white">
              Trang chủ
            </Link>
            {crumbs.map((c) => (
              <span key={c.label} className="flex items-center gap-1.5">
                <span aria-hidden>›</span>
                {c.to ? (
                  <Link to={c.to} className="text-white/80 hover:text-white">
                    {c.label}
                  </Link>
                ) : (
                  <span className="font-semibold text-white">{c.label}</span>
                )}
              </span>
            ))}
          </nav>
        )}
        <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            {kicker && (
              <span className="mb-2 inline-flex rounded-full bg-sun px-3 py-1 text-[12.5px] font-bold uppercase tracking-[0.06em] text-ink shadow-card">
                {kicker}
              </span>
            )}
            <h1 className="sticker-text-sm font-display text-[32px] font-extrabold leading-[1.08] sm:text-[42px]">
              {title}
            </h1>
            {subtitle && <p className="mt-2 max-w-2xl text-[15px] text-white/90">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap gap-2.5">{actions}</div>}
        </div>
        {children}
      </div>
      <TornEdge className="absolute inset-x-0 bottom-0" seed={17} />
    </section>
  )
}

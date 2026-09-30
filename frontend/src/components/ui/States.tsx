import type { ReactNode } from 'react'
import { Mascot } from '@/components/decor/Decor'
import { Button, Spinner } from './Button'

export function EmptyState({
  title,
  description,
  action,
  icon,
}: {
  title: string
  description?: ReactNode
  action?: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      {icon ? (
        <div className="relative mb-4" aria-hidden>
          <span className="absolute -right-2 -top-1 h-4 w-4 rounded-full bg-sun" />
          <span className="absolute -bottom-1 -left-3 h-3 w-3 rounded-full bg-skill-creative" />
          <div className="grid h-20 w-20 animate-float place-items-center rounded-full border-4 border-sky/40 bg-sky-soft text-[34px]">
            {icon}
          </div>
        </div>
      ) : (
        <Mascot className="mb-3 w-24 animate-float" />
      )}
      <p className="font-display text-[20px] font-extrabold leading-tight">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-[14px] text-ink-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <div
        className="mb-4 grid h-16 w-16 animate-wiggle place-items-center rounded-full border-4 border-danger/30 bg-danger-soft font-display text-[30px] font-extrabold text-danger"
        aria-hidden
      >
        !
      </div>
      <p className="font-display text-[20px] font-extrabold">Ối, không tải được dữ liệu</p>
      <p className="mt-1.5 max-w-sm text-[14px] text-ink-muted">{message ?? 'Kiểm tra kết nối mạng rồi thử lại.'}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" className="mt-5" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  )
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-2xl bg-muted ${className}`} aria-hidden />
}

export function PageLoader() {
  return (
    <div className="flex flex-col items-center gap-3 py-20 text-primary" role="status" aria-label="Đang tải">
      <Mascot className="w-20 animate-float" />
      <span className="flex items-center gap-2 text-[13px] font-semibold text-ink-muted">
        <Spinner className="h-4 w-4 text-primary" /> Đang tải…
      </span>
    </div>
  )
}

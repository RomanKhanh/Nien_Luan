import type { ReactNode } from 'react'
import { Button, Spinner } from './Button'

export function EmptyState({
  title,
  description,
  action,
  icon = '◇',
}: {
  title: string
  description?: ReactNode
  action?: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <div
        className="mb-4 grid h-14 w-14 place-items-center rounded-lg bg-muted text-[22px] text-ink-muted"
        aria-hidden
      >
        {icon}
      </div>
      <p className="text-[16px] font-bold">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-[14px] text-ink-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center px-6 py-12 text-center">
      <div
        className="mb-4 grid h-14 w-14 place-items-center rounded-lg bg-danger-soft text-[22px] font-bold text-danger"
        aria-hidden
      >
        !
      </div>
      <p className="text-[16px] font-bold">Không tải được dữ liệu</p>
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
  return <div className={`animate-pulse rounded-md bg-muted ${className}`} aria-hidden />
}

export function PageLoader() {
  return (
    <div className="flex justify-center py-20 text-primary" role="status" aria-label="Đang tải">
      <Spinner className="h-7 w-7" />
    </div>
  )
}

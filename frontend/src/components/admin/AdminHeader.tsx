import type { ReactNode } from 'react'

export function AdminHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="h1">{title}</h1>
        {/* gạch màu 4 nhóm kỹ năng dưới tiêu đề */}
        <div className="mt-1.5 flex h-1.5 w-24 overflow-hidden rounded-full" aria-hidden>
          <span className="flex-1 bg-skill-logic" />
          <span className="flex-1 bg-skill-creative" />
          <span className="flex-1 bg-skill-solve" />
          <span className="flex-1 bg-skill-stem" />
        </div>
        {description && <p className="mt-2 text-[14px] text-ink-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

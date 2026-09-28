import type { ReactNode } from 'react'
import type { ComplaintStatus, OrderStatus } from '@/api/types'
import { formatDecimal } from '@/lib/format'
import { COMPLAINT_STATUS, ORDER_STATUS } from '@/lib/labels'
import { skillTheme } from '@/lib/skills'

export function SkillBadge({ code, name, value }: { code: string; name?: string; value?: number }) {
  const theme = skillTheme(code, name)
  return (
    <span
      className="inline-flex items-center gap-1 rounded-sm px-2 py-0.5 text-[12px] font-semibold"
      style={{ background: theme.soft, color: theme.color }}
    >
      {theme.short || name}
      {value !== undefined && <span>{value}</span>}
    </span>
  )
}

export function Stars({ value, size = 14 }: { value: number; size?: number }) {
  const full = Math.round(value)
  return (
    <span
      className="inline-flex text-accent"
      style={{ fontSize: size }}
      aria-label={`${formatDecimal(value)} trên 5 sao`}
    >
      {[1, 2, 3, 4, 5].map((i) => (
        <span key={i} className={i <= full ? '' : 'text-line-strong'} aria-hidden>
          ★
        </span>
      ))}
    </span>
  )
}

const TONES = {
  gray: 'bg-muted text-ink-2',
  blue: 'bg-skill-logic-soft text-skill-logic',
  amber: 'bg-warning-soft text-[#B45309]',
  violet: 'bg-primary-soft text-primary-hover',
  green: 'bg-success-soft text-success',
  red: 'bg-danger-soft text-danger',
}

export function Pill({ tone, children }: { tone: keyof typeof TONES; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[12px] font-semibold ${TONES[tone]}`}>
      {children}
    </span>
  )
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const s = ORDER_STATUS[status]
  return <Pill tone={s.tone}>{s.label}</Pill>
}

export function ComplaintStatusBadge({ status }: { status: ComplaintStatus }) {
  const s = COMPLAINT_STATUS[status]
  return <Pill tone={s.tone}>{s.label}</Pill>
}

// thanh chỉ số kỹ năng 0-10 (mockup "Thanh chỉ số kỹ năng")
export function SkillBar({ code, name, value, max = 10 }: { code: string; name: string; value: number; max?: number }) {
  const theme = skillTheme(code, name)
  const pct = Math.max(0, Math.min(100, (value / max) * 100))
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between text-[13.5px]">
        <span className="font-semibold text-ink-2">{name}</span>
        <span className="font-bold" style={{ color: theme.color }}>
          {Number.isInteger(value) ? value : formatDecimal(value)}/{max}
        </span>
      </div>
      <div className="h-2 overflow-hidden rounded-full" style={{ background: theme.soft }}>
        <div className="h-full rounded-full transition-[width]" style={{ width: `${pct}%`, background: theme.color }} />
      </div>
    </div>
  )
}

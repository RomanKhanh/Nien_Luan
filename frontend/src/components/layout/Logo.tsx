import { Link } from 'react-router-dom'

export function Logo({ to = '/', suffix }: { to?: string; suffix?: string }) {
  return (
    <Link to={to} className="flex items-center gap-2.5 text-ink hover:text-ink" aria-label="BrainBlocks - trang chủ">
      <span className="grid h-9 w-9 place-items-center rounded-[10px] bg-primary" aria-hidden>
        <span className="h-3.5 w-3.5 rotate-12 rounded-[3px] bg-accent" />
      </span>
      <span className="text-[19px] font-extrabold tracking-[-0.02em]">
        BrainBlocks
        {suffix && <span className="ml-1.5 text-[13px] font-semibold text-ink-muted">{suffix}</span>}
      </span>
    </Link>
  )
}

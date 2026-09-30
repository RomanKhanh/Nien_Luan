import { Link } from 'react-router-dom'

export function Logo({ to = '/', suffix }: { to?: string; suffix?: string }) {
  return (
    <Link to={to} className="flex items-center gap-2.5 text-ink hover:text-ink" aria-label="BrainBlocks - trang chủ">
      <span
        className="grid h-9 w-9 place-items-center rounded-[12px] bg-primary shadow-[0_3px_0_#3a22b8] transition hover:rotate-6"
        aria-hidden
      >
        <span className="h-3.5 w-3.5 rotate-12 rounded-[3px] bg-accent" />
      </span>
      <span className="font-display text-[23px] font-extrabold leading-none tracking-[-0.01em]">
        BrainBlocks
        {suffix && <span className="ml-1.5 font-sans text-[13px] font-semibold text-ink-muted">{suffix}</span>}
      </span>
    </Link>
  )
}

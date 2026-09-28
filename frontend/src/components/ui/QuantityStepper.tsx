export function QuantityStepper({
  value,
  min = 1,
  max,
  onChange,
  disabled,
  size = 'md',
}: {
  value: number
  min?: number
  max: number
  onChange: (value: number) => void
  disabled?: boolean
  size?: 'sm' | 'md'
}) {
  const h = size === 'sm' ? 'h-8' : 'h-11'
  const btn = `grid ${h} w-9 place-items-center text-[18px] text-ink-2 hover:bg-muted disabled:text-ink-faint disabled:hover:bg-transparent`
  return (
    <div
      className={`inline-flex items-center overflow-hidden rounded-md border-[1.5px] border-line-strong bg-surface ${h}`}
    >
      <button
        type="button"
        className={btn}
        disabled={disabled || value <= min}
        onClick={() => onChange(value - 1)}
        aria-label="Giảm"
      >
        −
      </button>
      <span className="w-9 text-center text-[14px] font-semibold" aria-live="polite">
        {value}
      </span>
      <button
        type="button"
        className={btn}
        disabled={disabled || value >= max}
        onClick={() => onChange(value + 1)}
        aria-label="Tăng"
      >
        +
      </button>
    </div>
  )
}

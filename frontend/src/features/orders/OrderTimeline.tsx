import type { OrderStatus } from '@/api/types'
import { ORDER_FLOW, ORDER_STATUS } from '@/lib/labels'

const STEP_ICONS = ['📝', '✅', '🚚', '🎁']

// thanh tiến trình trạng thái đơn: Chờ xác nhận -> Đã xác nhận -> Đang giao -> Đã giao
export function OrderTimeline({ status }: { status: OrderStatus }) {
  if (status === 'CANCELLED') {
    return (
      <p className="rounded-2xl bg-danger-soft px-4 py-3 text-[14px] font-bold text-danger">✕ Đơn hàng đã bị huỷ</p>
    )
  }
  const current = ORDER_FLOW.indexOf(status)
  return (
    <ol className="grid grid-cols-4 gap-1">
      {ORDER_FLOW.map((s, i) => {
        const done = i <= current
        return (
          <li key={s} className="flex flex-col items-center text-center">
            <div className="flex w-full items-center">
              <span className={`h-1 flex-1 rounded-full ${i === 0 ? 'invisible' : done ? 'bg-primary' : 'bg-line'}`} />
              <span
                className={`grid h-11 w-11 shrink-0 place-items-center rounded-full border-4 text-[18px] ${
                  done
                    ? 'border-white bg-primary text-white shadow-[0_3px_0_#3a22b8]'
                    : 'border-white bg-muted text-ink-faint grayscale'
                } ${i === current ? 'animate-float' : ''}`}
                aria-hidden
              >
                {STEP_ICONS[i] ?? i + 1}
              </span>
              <span
                className={`h-1 flex-1 rounded-full ${i === ORDER_FLOW.length - 1 ? 'invisible' : i < current ? 'bg-primary' : 'bg-line'}`}
              />
            </div>
            <span className={`mt-1.5 text-[12px] font-semibold sm:text-[13px] ${done ? 'text-ink' : 'text-ink-faint'}`}>
              {ORDER_STATUS[s].label}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

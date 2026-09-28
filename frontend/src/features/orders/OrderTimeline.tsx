import type { OrderStatus } from '@/api/types'
import { ORDER_FLOW, ORDER_STATUS } from '@/lib/labels'

// thanh tiến trình trạng thái đơn: Chờ xác nhận -> Đã xác nhận -> Đang giao -> Đã giao
export function OrderTimeline({ status }: { status: OrderStatus }) {
  if (status === 'CANCELLED') {
    return (
      <p className="rounded-md bg-danger-soft px-4 py-3 text-[14px] font-semibold text-danger">Đơn hàng đã bị huỷ</p>
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
              <span className={`h-0.5 flex-1 ${i === 0 ? 'invisible' : done ? 'bg-primary' : 'bg-line'}`} />
              <span
                className={`grid h-7 w-7 shrink-0 place-items-center rounded-full text-[12px] font-bold ${
                  done ? 'bg-primary text-white' : 'bg-muted text-ink-faint'
                }`}
                aria-hidden
              >
                {done ? '✓' : i + 1}
              </span>
              <span
                className={`h-0.5 flex-1 ${i === ORDER_FLOW.length - 1 ? 'invisible' : i < current ? 'bg-primary' : 'bg-line'}`}
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

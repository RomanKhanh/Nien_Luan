import type { ReactNode } from 'react'
import type { Order } from '@/api/types'
import { formatPrice } from '@/lib/format'

// 3 dòng tiền: tạm tính (tiền hàng) / phí vận chuyển / tổng thanh toán
export function MoneySummary({
  subtotal,
  shippingValue,
  total,
  totalNote,
  className = '',
}: {
  subtotal: number
  shippingValue: ReactNode
  total: number
  // ghi chú dưới tổng, vd "Chưa gồm phí vận chuyển"
  totalNote?: ReactNode
  className?: string
}) {
  return (
    <div className={className}>
      <dl className="space-y-1.5 text-[14px]">
        <div className="flex justify-between gap-3">
          <dt className="text-ink-2">Tạm tính</dt>
          <dd className="tabular-nums">{formatPrice(subtotal)}</dd>
        </div>
        <div className="flex justify-between gap-3">
          <dt className="text-ink-2">Phí vận chuyển</dt>
          <dd className="text-right tabular-nums">{shippingValue}</dd>
        </div>
      </dl>
      <div className="mt-3 flex items-baseline justify-between border-t-2 border-dashed border-line pt-3 text-[16px] font-bold">
        <span>Tổng thanh toán</span>
        <span className="font-display text-[24px] font-extrabold leading-none text-coral">{formatPrice(total)}</span>
      </div>
      {totalNote && <p className="mt-1 text-right text-[12px] text-ink-muted">{totalNote}</p>}
    </div>
  )
}

// tiền của một đơn đã đặt (khách + admin)
export function OrderMoneySummary({ order, className }: { order: Order; className?: string }) {
  return (
    <MoneySummary
      className={className}
      subtotal={order.subtotal}
      shippingValue={formatPrice(order.shippingFee)}
      total={order.totalAmount}
    />
  )
}

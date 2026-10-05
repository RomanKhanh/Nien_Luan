import type { ReactNode } from 'react'
import type { Order } from '@/api/types'
import { formatPrice } from '@/lib/format'

// Các dòng tiền: tạm tính (tiền hàng) / phí vận chuyển / các khoản giảm nhờ voucher / tổng thanh toán
export function MoneySummary({
  subtotal,
  shippingValue,
  shippingDiscount = 0,
  discountAmount = 0,
  total,
  totalNote,
  className = '',
}: {
  subtotal: number
  shippingValue: ReactNode
  // voucher freeship
  shippingDiscount?: number
  // voucher giảm giá
  discountAmount?: number
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
        {shippingDiscount > 0 && (
          <div className="flex justify-between gap-3 text-success">
            <dt>Voucher miễn phí vận chuyển</dt>
            <dd className="tabular-nums">−{formatPrice(shippingDiscount)}</dd>
          </div>
        )}
        {discountAmount > 0 && (
          <div className="flex justify-between gap-3 text-success">
            <dt>Voucher giảm giá</dt>
            <dd className="tabular-nums">−{formatPrice(discountAmount)}</dd>
          </div>
        )}
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
      shippingDiscount={order.shippingDiscount}
      discountAmount={order.discountAmount}
      total={order.totalAmount}
    />
  )
}

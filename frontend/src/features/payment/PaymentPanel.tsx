import { useQuery } from '@tanstack/react-query'
import { paymentApi } from '@/api/endpoints'
import type { Order } from '@/api/types'
import { Pill } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { formatDateTime } from '@/lib/format'
import { PAYMENT_STATUS } from '@/lib/labels'
import { paymentKey, usePayWithMomo } from './usePayWithMomo'

// Trạng thái thanh toán của đơn MoMo trong trang chi tiết đơn + nút thanh toán (lại).
// Đơn COD không có khối này.
export function PaymentPanel({ order }: { order: Order }) {
  const toast = useToast()
  const payment = useQuery({
    queryKey: paymentKey(order.id),
    queryFn: () => paymentApi.status(order.id),
    // vừa từ MoMo quay về: IPN có thể tới chậm vài giây nên hỏi lại khi còn chờ
    refetchInterval: (q) => (q.state.data?.status === 'PENDING' ? 5000 : false),
  })
  const pay = usePayWithMomo()

  if (payment.isPending) return <Skeleton className="h-24" />

  // null = chưa từng tạo link thanh toán
  const status = payment.data?.status ?? 'PENDING'
  const paid = status === 'SUCCESS'
  const canPay = !paid && order.status !== 'CANCELLED'
  const label = PAYMENT_STATUS[status]

  return (
    <section
      className={`card p-5 text-[14px] ${paid ? 'border-[#BFE6CD] bg-success-soft/60' : 'border-[#f4c9de] bg-[#fdf1f7]'}`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 font-display text-[19px] font-extrabold">
          <MomoMark /> Thanh toán MoMo
        </h2>
        {payment.isError ? <Pill tone="gray">Chưa rõ trạng thái</Pill> : <Pill tone={label.tone}>{label.label}</Pill>}
      </div>
      {payment.isError ? (
        <p className="mt-2 text-ink-muted">Không tải được trạng thái thanh toán: {payment.error.message}</p>
      ) : paid ? (
        <p className="mt-2 text-ink-2">
          Đã thanh toán lúc <b>{formatDateTime(payment.data?.paidAt)}</b>
          {payment.data?.transactionId && (
            <>
              {' '}
              · mã giao dịch <b>{payment.data.transactionId}</b>
            </>
          )}
        </p>
      ) : order.status === 'CANCELLED' ? (
        <p className="mt-2 text-ink-muted">Đơn đã huỷ nên không cần thanh toán.</p>
      ) : (
        <p className="mt-2 text-ink-2">
          {status === 'FAILED'
            ? 'Lần thanh toán trước không thành công. Bạn có thể thanh toán lại.'
            : 'Đơn chưa được thanh toán. Nếu bạn vừa thanh toán xong, trạng thái sẽ tự cập nhật sau ít giây.'}
        </p>
      )}
      {canPay && (
        <Button
          block
          className="mt-4 !bg-[#a50064] !shadow-[0_4px_0_#73003f] hover:!bg-[#8d0055]"
          loading={pay.isPending}
          onClick={() => pay.mutate(order.id, { onError: (e) => toast.error(e.message) })}
        >
          {status === 'FAILED' ? 'Thanh toán lại bằng MoMo' : 'Thanh toán bằng MoMo'}
        </Button>
      )}
    </section>
  )
}

// ô vuông hồng chữ "mo mo" gợi nhận diện ví MoMo (tự vẽ, không dùng logo chính thức)
export function MomoMark({ className = 'h-7 w-7 text-[9px]' }: { className?: string }) {
  return (
    <span
      className={`grid shrink-0 place-items-center rounded-lg bg-[#a50064] font-sans font-extrabold leading-[1] text-white ${className}`}
      aria-hidden
    >
      <span className="text-center">
        mo
        <br />
        mo
      </span>
    </span>
  )
}

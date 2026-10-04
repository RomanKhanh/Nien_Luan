import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'
import { feedbackApi, orderApi } from '@/api/endpoints'
import { OrderStatusBadge } from '@/components/ui/Badges'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/Modal'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { cartKey } from '@/features/cart/useCart'
import { ComplaintModal } from '@/features/complaints/ComplaintModal'
import { OrderMoneySummary } from '@/features/orders/MoneySummary'
import { OrderTimeline } from '@/features/orders/OrderTimeline'
import { UnboxingVideoReminder } from '@/features/orders/UnboxingVideoReminder'
import { PaymentPanel } from '@/features/payment/PaymentPanel'
import { WriteReviewModal } from '@/features/reviews/WriteReviewModal'
import { voucherKey } from '@/features/vouchers/vouchers'
import { formatDateTime, formatPrice } from '@/lib/format'
import { PAYMENT_METHOD } from '@/lib/labels'
import { AccountShell } from '../account/AccountShell'

export default function OrderDetailPage() {
  const id = Number(useParams().id)
  const location = useLocation()
  const justPlaced = Boolean((location.state as { placed?: boolean } | null)?.placed)
  const toast = useToast()
  const queryClient = useQueryClient()
  const order = useQuery({ queryKey: ['order', id], queryFn: () => orderApi.get(id), enabled: id > 0 })
  const myReviews = useQuery({ queryKey: ['my-reviews'], queryFn: feedbackApi.myReviews })
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [complaintOpen, setComplaintOpen] = useState(false)
  const [reviewing, setReviewing] = useState<{ productId: number; productName: string } | null>(null)

  const cancel = useMutation({
    mutationFn: () => orderApi.cancel(id),
    onSuccess: (data) => {
      queryClient.setQueryData(['order', id], data)
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: cartKey })
      queryClient.invalidateQueries({ queryKey: voucherKey })
      toast.success('Đã huỷ đơn hàng, sản phẩm đã được đưa lại vào giỏ hàng')
      setConfirmCancel(false)
    },
    onError: (e) => toast.error(e.message),
  })

  if (order.isPending) return <PageLoader />
  if (order.isError) {
    return (
      <AccountShell>
        {order.error.status === 404 ? (
          <EmptyState title="Không tìm thấy đơn hàng" action={<ButtonLink to="/orders">Về danh sách đơn</ButtonLink>} />
        ) : (
          <ErrorState message={order.error.message} onRetry={() => order.refetch()} />
        )}
      </AccountShell>
    )
  }

  const o = order.data
  const reviewed = new Set(myReviews.data?.map((r) => r.productId))
  // chờ xác nhận: khách tự huỷ bằng nút, không cần gửi yêu cầu; đã xác nhận: chỉ huỷ qua yêu cầu;
  // đang giao: không huỷ được nữa (khớp OrderService.cancelOrder và COMPLAINT_ALLOWED.CANCEL)
  const canCancel = o.status === 'PENDING'
  // đơn khách tự huỷ lúc chờ xác nhận: hàng đã về giỏ, không còn gì để phản hồi (ComplaintService cũng chặn)
  const showFeedback = !canCancel && !o.cancelledByCustomer

  return (
    <AccountShell>
      {justPlaced && (
        <div className="relative mb-6 overflow-hidden rounded-[22px] border-2 border-[#BFE6CD] bg-success-soft p-5">
          <span className="absolute -right-2 -top-3 animate-float text-[56px]" aria-hidden>
            🎉
          </span>
          <p className="font-display text-[24px] font-extrabold text-success">Đặt hàng thành công!</p>
          <p className="mt-1 text-[14px] text-ink-2">
            Mã đơn <b>{o.orderCode}</b>. BrainBlocks sẽ xác nhận đơn sớm và giao hàng tới địa chỉ của bạn.
          </p>
          <div className="mt-4">
            <UnboxingVideoReminder />
          </div>
          <div className="mt-4 flex flex-wrap gap-2.5">
            <ButtonLink to="/orders" size="sm">
              Xem đơn hàng
            </ButtonLink>
            <ButtonLink to="/products" size="sm" variant="secondary">
              Tiếp tục mua sắm
            </ButtonLink>
          </div>
        </div>
      )}

      <nav className="text-[13px] text-ink-muted">
        <Link to="/orders" className="text-ink-muted hover:text-ink">
          Đơn hàng của tôi
        </Link>{' '}
        / <span className="text-ink-2">{o.orderCode}</span>
      </nav>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <h1 className="h1">Đơn {o.orderCode}</h1>
        <OrderStatusBadge status={o.status} />
      </div>
      <p className="mt-1 text-[13.5px] text-ink-muted">Đặt lúc {formatDateTime(o.createdAt)}</p>

      <div className="card mt-5 bg-sky-soft/50 p-5">
        <OrderTimeline status={o.status} />
      </div>

      <div className="mt-5 grid gap-5 md:grid-cols-[1.4fr_1fr]">
        <section className="card p-5">
          <h2 className="mb-3 font-display text-[19px] font-extrabold">Sản phẩm</h2>
          <ul className="divide-y divide-line">
            {o.items.map((item) => (
              <li key={item.id} className="flex flex-wrap items-center gap-3 py-3">
                <div className="min-w-0 flex-1">
                  <Link to={`/products/${item.productId}`} className="font-semibold text-ink hover:text-primary">
                    {item.productName}
                  </Link>
                  <p className="text-[13px] text-ink-muted">
                    {formatPrice(item.unitPrice)} × {item.quantity}
                    {item.childName && ` · mua cho bé ${item.childName}`}
                  </p>
                </div>
                <span className="font-semibold">{formatPrice(item.subtotal)}</span>
                {o.status === 'DELIVERED' &&
                  (reviewed.has(item.productId) ? (
                    <span className="text-[12.5px] font-semibold text-success">✓ Đã đánh giá</span>
                  ) : (
                    <Button
                      size="sm"
                      variant="accent"
                      onClick={() => setReviewing({ productId: item.productId, productName: item.productName })}
                    >
                      Đánh giá
                    </Button>
                  ))}
              </li>
            ))}
          </ul>
          <OrderMoneySummary order={o} className="mt-3 border-t border-line pt-3" />
        </section>

        <div className="space-y-5">
          <section className="card p-5 text-[14px]">
            <h2 className="mb-3 font-display text-[19px] font-extrabold">Giao hàng & thanh toán</h2>
            <p className="font-semibold">
              {o.receiverName} · {o.receiverPhone}
            </p>
            <p className="mt-1 text-ink-2">{o.shippingAddress}</p>
            <p className="mt-3 text-ink-muted">{PAYMENT_METHOD[o.paymentMethod]}</p>
          </section>
          {o.paymentMethod === 'MOMO' && <PaymentPanel order={o} />}
          {(canCancel || showFeedback) && (
            <section className="card space-y-2.5 p-5">
              {canCancel && (
                <Button variant="danger-outline" block onClick={() => setConfirmCancel(true)}>
                  Huỷ đơn hàng
                </Button>
              )}
              {showFeedback && (
                <Button variant="secondary" block onClick={() => setComplaintOpen(true)}>
                  {o.status === 'DELIVERED' ? 'Yêu cầu đổi / trả · khiếu nại' : 'Gửi phản hồi về đơn'}
                </Button>
              )}
              {o.status === 'CONFIRMED' && (
                <p className="text-[12.5px] text-ink-muted">
                  Đơn đã được xác nhận nên không tự huỷ được; muốn huỷ hãy gửi yêu cầu huỷ đơn.
                </p>
              )}
              {o.status === 'SHIPPING' && (
                <p className="text-[12.5px] text-ink-muted">Đơn đang giao nên không thể huỷ.</p>
              )}
            </section>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmCancel}
        title="Huỷ đơn hàng này?"
        message="Sản phẩm trong đơn sẽ được đưa lại vào giỏ hàng để bạn chỉnh sửa và đặt lại. Thao tác không thể hoàn tác."
        confirmLabel="Huỷ đơn"
        loading={cancel.isPending}
        onConfirm={() => cancel.mutate()}
        onClose={() => setConfirmCancel(false)}
      />
      <ComplaintModal open={complaintOpen} onClose={() => setComplaintOpen(false)} order={o} />
      {reviewing && (
        <WriteReviewModal
          open
          onClose={() => setReviewing(null)}
          productId={reviewing.productId}
          productName={reviewing.productName}
        />
      )}
    </AccountShell>
  )
}

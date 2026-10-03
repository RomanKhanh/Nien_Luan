import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { OrderStatus } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { OrderStatusBadge } from '@/components/ui/Badges'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog, Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { useToast } from '@/components/ui/Toast'
import { OrderMoneySummary } from '@/features/orders/MoneySummary'
import { OrderTimeline } from '@/features/orders/OrderTimeline'
import { formatDateTime, formatPrice } from '@/lib/format'
import { ORDER_NEXT, ORDER_STATUS, PAYMENT_METHOD } from '@/lib/labels'

const TABS: (OrderStatus | undefined)[] = [undefined, 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED']

export default function AdminOrdersPage() {
  const [status, setStatus] = useState<OrderStatus | undefined>()
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const params = { status, page, size: 15 }
  const orders = useQuery({
    queryKey: ['admin', 'orders', params],
    queryFn: () => adminApi.orders(params),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <AdminHeader
        title="Đơn hàng"
        description="Xem chi tiết và cập nhật trạng thái đơn theo luồng Chờ xác nhận → Đã giao."
      />
      <div className="card overflow-hidden">
        <div className="flex gap-1.5 overflow-x-auto border-b border-line p-3" role="tablist">
          {TABS.map((t) => (
            <button
              key={t ?? 'ALL'}
              type="button"
              role="tab"
              aria-selected={status === t}
              onClick={() => {
                setStatus(t)
                setPage(0)
              }}
              className={`shrink-0 rounded-full px-3.5 py-1.5 text-[13px] font-semibold ${
                status === t ? 'bg-ink text-white' : 'text-ink-2 hover:bg-muted'
              }`}
            >
              {t ? ORDER_STATUS[t].label : 'Tất cả'}
            </button>
          ))}
        </div>
        {orders.isPending ? (
          <PageLoader />
        ) : orders.isError ? (
          <ErrorState message={orders.error.message} onRetry={() => orders.refetch()} />
        ) : orders.data.content.length === 0 ? (
          <EmptyState title="Không có đơn hàng" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-[14px]">
              <thead className="bg-muted/60 text-[12.5px] uppercase tracking-[0.04em] text-ink-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Mã đơn</th>
                  <th className="px-4 py-3 font-semibold">Khách hàng</th>
                  <th className="px-4 py-3 font-semibold">Ngày đặt</th>
                  <th className="px-4 py-3 font-semibold">Tổng tiền</th>
                  <th className="px-4 py-3 font-semibold">Trạng thái</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className={`divide-y divide-line ${orders.isPlaceholderData ? 'opacity-60' : ''}`}>
                {orders.data.content.map((o) => (
                  <tr key={o.id}>
                    <td className="px-4 py-3 font-semibold">{o.orderCode}</td>
                    <td className="px-4 py-3">
                      <p>{o.customerName}</p>
                      <p className="text-[12.5px] text-ink-muted">{o.customerEmail}</p>
                    </td>
                    <td className="px-4 py-3 text-ink-2">{formatDateTime(o.createdAt)}</td>
                    <td className="px-4 py-3 tabular-nums">{formatPrice(o.totalAmount)}</td>
                    <td className="px-4 py-3">
                      <OrderStatusBadge status={o.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" variant="secondary" onClick={() => setSelected(o.id)}>
                        Chi tiết
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {orders.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={orders.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>
      {selected !== null && <OrderDetailDrawer orderId={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function OrderDetailDrawer({ orderId, onClose }: { orderId: number; onClose: () => void }) {
  const order = useQuery({ queryKey: ['admin', 'order', orderId], queryFn: () => adminApi.order(orderId) })
  const [target, setTarget] = useState<OrderStatus | null>(null)
  const toast = useToast()
  const queryClient = useQueryClient()
  const update = useMutation({
    mutationFn: (status: OrderStatus) => adminApi.updateOrderStatus(orderId, status),
    onSuccess: (data) => {
      queryClient.setQueryData(['admin', 'order', orderId], data)
      queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'stats'] })
      toast.success(`Đã chuyển đơn sang “${ORDER_STATUS[data.status].label}”`)
      setTarget(null)
    },
    onError: (e) => {
      toast.error(e.message)
      setTarget(null)
    },
  })

  const o = order.data
  return (
    <Modal open onClose={onClose} variant="drawer" size="lg" title={o ? `Đơn ${o.orderCode}` : 'Chi tiết đơn'}>
      {order.isPending ? (
        <PageLoader />
      ) : order.isError ? (
        <ErrorState message={order.error.message} />
      ) : (
        o && (
          <div className="space-y-5 text-[14px]">
            <OrderTimeline status={o.status} />
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <p className="text-[12.5px] font-semibold text-ink-muted">Người nhận</p>
                <p className="font-semibold">
                  {o.receiverName} · {o.receiverPhone}
                </p>
                <p className="text-ink-2">{o.shippingAddress}</p>
              </div>
              <div>
                <p className="text-[12.5px] font-semibold text-ink-muted">Thanh toán</p>
                <p>{PAYMENT_METHOD[o.paymentMethod]}</p>
                <p className="text-ink-muted">Đặt lúc {formatDateTime(o.createdAt)}</p>
              </div>
            </div>
            <ul className="divide-y divide-line rounded-md border border-line">
              {o.items.map((i) => (
                <li key={i.id} className="flex justify-between gap-3 px-4 py-2.5">
                  <span>
                    {i.productName} × {i.quantity}
                    {i.childName && <span className="block text-[12.5px] text-ink-muted">Cho bé {i.childName}</span>}
                  </span>
                  <span className="shrink-0 tabular-nums">{formatPrice(i.subtotal)}</span>
                </li>
              ))}
            </ul>
            <OrderMoneySummary order={o} className="rounded-md border border-line px-4 py-3" />
            <div>
              <p className="mb-2 font-bold">Cập nhật trạng thái</p>
              {ORDER_NEXT[o.status].length === 0 ? (
                <p className="text-ink-muted">Đơn đã ở trạng thái cuối, không đổi được nữa.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {ORDER_NEXT[o.status].map((s) => (
                    <Button
                      key={s}
                      variant={s === 'CANCELLED' ? 'danger-outline' : 'primary'}
                      onClick={() => setTarget(s)}
                    >
                      {s === 'CANCELLED' ? 'Huỷ đơn' : `Chuyển sang “${ORDER_STATUS[s].label}”`}
                    </Button>
                  ))}
                </div>
              )}
              {o.status === 'SHIPPING' && (
                <p className="mt-2 text-[12.5px] text-ink-muted">
                  Khi đơn “Đã giao”, sản phẩm được gắn cho bé sẽ tự vào hồ sơ kỹ năng của bé.
                </p>
              )}
            </div>
          </div>
        )
      )}
      <ConfirmDialog
        open={target !== null}
        title={target === 'CANCELLED' ? 'Huỷ đơn hàng?' : 'Xác nhận đổi trạng thái'}
        message={
          target === 'CANCELLED'
            ? 'Sản phẩm sẽ được hoàn kho. Không thể hoàn tác.'
            : `Chuyển đơn sang “${target ? ORDER_STATUS[target].label : ''}”? Trạng thái không quay lại được.`
        }
        confirmLabel={target === 'CANCELLED' ? 'Huỷ đơn' : 'Xác nhận'}
        tone={target === 'CANCELLED' ? 'danger' : 'primary'}
        loading={update.isPending}
        onConfirm={() => target && update.mutate(target)}
        onClose={() => setTarget(null)}
      />
    </Modal>
  )
}

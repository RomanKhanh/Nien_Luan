import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { orderApi } from '@/api/endpoints'
import type { OrderStatus } from '@/api/types'
import { OrderStatusBadge } from '@/components/ui/Badges'
import { ButtonLink } from '@/components/ui/Button'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { formatDateTime, formatPrice } from '@/lib/format'
import { ORDER_STATUS } from '@/lib/labels'
import { AccountShell } from '../account/AccountShell'

const TABS: (OrderStatus | 'ALL')[] = ['ALL', 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED']

export default function OrdersPage() {
  const orders = useQuery({ queryKey: ['orders'], queryFn: orderApi.list })
  const [tab, setTab] = useState<OrderStatus | 'ALL'>('ALL')
  const list = orders.data?.filter((o) => tab === 'ALL' || o.status === tab) ?? []

  return (
    <AccountShell>
      <h1 className="h1 mb-4">Đơn hàng của tôi</h1>
      <div className="-mx-1 mb-4 flex gap-1.5 overflow-x-auto pb-1" role="tablist">
        {TABS.map((t) => {
          const count = t === 'ALL' ? orders.data?.length : orders.data?.filter((o) => o.status === t).length
          return (
            <button
              key={t}
              type="button"
              role="tab"
              aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={`shrink-0 rounded-full px-4 py-1.5 text-[13px] font-bold transition ${
                tab === t
                  ? 'bg-primary text-white shadow-[0_3px_0_#3a22b8]'
                  : 'bg-surface text-ink-2 ring-2 ring-line hover:text-primary hover:ring-primary'
              }`}
            >
              {t === 'ALL' ? 'Tất cả' : ORDER_STATUS[t].label}
              {count ? ` (${count})` : ''}
            </button>
          )
        })}
      </div>
      {orders.isPending ? (
        <PageLoader />
      ) : orders.isError ? (
        <ErrorState message={orders.error.message} onRetry={() => orders.refetch()} />
      ) : list.length === 0 ? (
        <div className="card">
          <EmptyState
            title={tab === 'ALL' ? 'Bạn chưa có đơn hàng nào' : 'Không có đơn ở trạng thái này'}
            action={tab === 'ALL' && <ButtonLink to="/products">Mua sắm ngay</ButtonLink>}
          />
        </div>
      ) : (
        <ul className="space-y-3">
          {list.map((o) => (
            <li key={o.id}>
              <Link
                to={`/orders/${o.id}`}
                className="card block border-l-[6px] border-l-sky p-4 text-ink transition hover:-translate-y-0.5 hover:shadow-card-hover hover:text-ink sm:p-5"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-bold">
                    {o.orderCode}{' '}
                    <span className="ml-1 text-[13px] font-normal text-ink-muted">{formatDateTime(o.createdAt)}</span>
                  </p>
                  <OrderStatusBadge status={o.status} />
                </div>
                <p className="mt-2 line-clamp-1 text-[14px] text-ink-2">
                  {o.items.map((i) => `${i.productName} × ${i.quantity}`).join(', ')}
                </p>
                <p className="mt-2 text-[14px]">
                  Tổng tiền:{' '}
                  <b className="font-display text-[17px] font-extrabold text-coral">{formatPrice(o.totalAmount)}</b>
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </AccountShell>
  )
}

import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/endpoints'
import type { VoucherStatus } from '@/api/types'
import { AdminHeader } from '@/components/admin/AdminHeader'
import { Pill } from '@/components/ui/Badges'
import { Pagination } from '@/components/ui/Pagination'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { voucherCondition, VOUCHER_REASON, VOUCHER_STATUS } from '@/features/vouchers/vouchers'
import { formatDate, formatDateTime } from '@/lib/format'

const TABS: (VoucherStatus | undefined)[] = [undefined, 'AVAILABLE', 'USED', 'EXPIRED']

// Admin chỉ xem voucher đã phát; quy tắc tặng nằm ở VoucherPolicy (backend)
export default function AdminVouchersPage() {
  const [status, setStatus] = useState<VoucherStatus | undefined>()
  const [page, setPage] = useState(0)
  const params = { status, page, size: 20 }
  const summary = useQuery({ queryKey: ['admin', 'vouchers', 'summary'], queryFn: adminApi.voucherSummary })
  const vouchers = useQuery({
    queryKey: ['admin', 'vouchers', params],
    queryFn: () => adminApi.vouchers(params),
    placeholderData: keepPreviousData,
  })

  const stats = summary.data && [
    { label: 'Đã phát', value: summary.data.issued },
    { label: 'Dùng được', value: summary.data.available },
    { label: 'Đã dùng', value: summary.data.used },
    { label: 'Hết hạn', value: summary.data.expired },
  ]

  return (
    <>
      <AdminHeader
        title="Voucher"
        description="Voucher tặng khách khi tạo tài khoản và khi bé đạt mốc kỹ năng. Đơn bị huỷ thì voucher được trả lại."
      />
      {stats && (
        <dl className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {stats.map((s) => (
            <div key={s.label} className="card p-4">
              <dt className="text-[13px] text-ink-muted">{s.label}</dt>
              <dd className="mt-1 font-display text-[26px] font-extrabold leading-none tabular-nums">{s.value}</dd>
            </div>
          ))}
        </dl>
      )}
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
              {t ? VOUCHER_STATUS[t].label : 'Tất cả'}
            </button>
          ))}
        </div>
        {vouchers.isPending ? (
          <PageLoader />
        ) : vouchers.isError ? (
          <ErrorState message={vouchers.error.message} onRetry={() => vouchers.refetch()} />
        ) : vouchers.data.content.length === 0 ? (
          <EmptyState title="Không có voucher" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-[14px]">
              <thead className="bg-muted/60 text-[12.5px] uppercase tracking-[0.04em] text-ink-muted">
                <tr>
                  <th className="px-4 py-3 font-semibold">Voucher</th>
                  <th className="px-4 py-3 font-semibold">Khách hàng</th>
                  <th className="px-4 py-3 font-semibold">Lý do tặng</th>
                  <th className="px-4 py-3 font-semibold">Ngày tặng · HSD</th>
                  <th className="px-4 py-3 font-semibold">Trạng thái</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-line ${vouchers.isPlaceholderData ? 'opacity-60' : ''}`}>
                {vouchers.data.content.map(({ voucher: v, customerName, customerEmail }) => (
                  <tr key={v.id}>
                    <td className="px-4 py-3">
                      <p className="font-semibold">{v.label}</p>
                      <p className="text-[12.5px] text-ink-muted">{voucherCondition(v)}</p>
                    </td>
                    <td className="px-4 py-3">
                      <p>{customerName}</p>
                      <p className="text-[12.5px] text-ink-muted">{customerEmail}</p>
                    </td>
                    <td className="px-4 py-3 text-ink-2">
                      {VOUCHER_REASON[v.reason]}
                      {v.childName && <span className="block text-[12.5px] text-ink-muted">bé {v.childName}</span>}
                    </td>
                    <td className="px-4 py-3 text-ink-2">
                      {formatDateTime(v.issuedAt)}
                      <span className="block text-[12.5px] text-ink-muted">HSD {formatDate(v.expiresAt)}</span>
                    </td>
                    <td className="px-4 py-3">
                      <Pill tone={VOUCHER_STATUS[v.status].tone}>{VOUCHER_STATUS[v.status].label}</Pill>
                      {v.orderCode && <span className="block text-[12.5px] text-ink-muted">{v.orderCode}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {vouchers.data && (
          <div className="border-t border-line p-4">
            <Pagination page={page} totalPages={vouchers.data.totalPages} onChange={setPage} />
          </div>
        )}
      </div>
    </>
  )
}

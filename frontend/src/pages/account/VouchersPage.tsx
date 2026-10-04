import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { voucherApi } from '@/api/endpoints'
import { Pill } from '@/components/ui/Badges'
import { EmptyState, ErrorState, PageLoader } from '@/components/ui/States'
import { VoucherTicket } from '@/features/vouchers/VoucherTicket'
import { voucherKey, VOUCHER_STATUS } from '@/features/vouchers/vouchers'
import { formatDate } from '@/lib/format'
import { AccountShell } from './AccountShell'

// quà theo mốc kỹ năng của từng bé (khớp VoucherPolicy ở backend)
const MILESTONES = [
  { level: 'Mới bắt đầu', gifts: 'Giảm 10%' },
  { level: 'Đang phát triển', gifts: 'Giảm 20% + Miễn phí vận chuyển' },
  { level: 'Phong phú', gifts: 'Giảm 20% + Giảm 100.000₫ + Miễn phí vận chuyển' },
]

export default function VouchersPage() {
  const vouchers = useQuery({ queryKey: voucherKey, queryFn: voucherApi.list })
  const available = vouchers.data?.filter((v) => v.status === 'AVAILABLE') ?? []
  const history = vouchers.data?.filter((v) => v.status !== 'AVAILABLE') ?? []

  return (
    <AccountShell>
      <h1 className="h1">Ví voucher</h1>
      <p className="mt-1 text-[14px] text-ink-muted">
        Chọn voucher ở bước thanh toán. Mỗi đơn dùng tối đa 1 voucher miễn phí vận chuyển và 1 voucher giảm giá; đơn bị
        huỷ thì voucher được trả lại.
      </p>

      <section className="mt-6">
        <h2 className="h2 mb-3">Dùng được ({available.length})</h2>
        {vouchers.isPending ? (
          <PageLoader />
        ) : vouchers.isError ? (
          <ErrorState message={vouchers.error.message} onRetry={() => vouchers.refetch()} />
        ) : available.length === 0 ? (
          <div className="card">
            <EmptyState title="Chưa có voucher nào dùng được" />
          </div>
        ) : (
          <ul className="grid gap-3 xl:grid-cols-2">
            {available.map((v) => (
              <li key={v.id}>
                <VoucherTicket voucher={v} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card mt-6 bg-sky-soft/50 p-5">
        <h2 className="font-display text-[19px] font-extrabold">Nhận thêm voucher theo hồ sơ kỹ năng của bé</h2>
        <p className="mt-1 text-[13.5px] text-ink-2">
          Mỗi bé, khi có <b>2 nhóm kỹ năng bất kỳ</b> đạt một mức trong <Link to="/children">hồ sơ kỹ năng</Link>, bạn
          nhận quà của mức đó (mỗi mức một lần cho mỗi bé). Chỉ tính đồ chơi đã mua và giao thành công cho bé, nên hãy
          chọn bé cho từng món ở bước thanh toán.
        </p>
        <ol className="mt-3 space-y-2">
          {MILESTONES.map((m, i) => (
            <li key={m.level} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[14px]">
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-sky font-display text-[13px] font-extrabold text-white">
                {i + 1}
              </span>
              <span className="font-bold">{m.level}</span>
              <span className="text-ink-2">→ {m.gifts}</span>
            </li>
          ))}
        </ol>
      </section>

      {history.length > 0 && (
        <section className="mt-6">
          <h2 className="h2 mb-3">Đã dùng / hết hạn</h2>
          <ul className="grid gap-3 xl:grid-cols-2">
            {history.map((v) => (
              <li key={v.id}>
                <VoucherTicket
                  voucher={v}
                  dimmed
                  aside={
                    <span className="flex flex-col items-end gap-1 text-[12.5px]">
                      <Pill tone={VOUCHER_STATUS[v.status].tone}>{VOUCHER_STATUS[v.status].label}</Pill>
                      {v.orderId && (
                        <Link to={`/orders/${v.orderId}`} className="font-semibold">
                          {v.orderCode}
                        </Link>
                      )}
                      {v.usedAt && <span className="text-ink-muted">{formatDate(v.usedAt)}</span>}
                    </span>
                  }
                />
              </li>
            ))}
          </ul>
        </section>
      )}
    </AccountShell>
  )
}

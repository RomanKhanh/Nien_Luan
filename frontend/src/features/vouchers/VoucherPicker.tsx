import { Link } from 'react-router-dom'
import type { Voucher } from '@/api/types'
import { formatPrice } from '@/lib/format'
import { VoucherTicket } from './VoucherTicket'
import { discountFor, ineligibleReason } from './vouchers'

// Chọn voucher ở bước thanh toán: tối đa 1 voucher freeship + 1 voucher giảm giá (khớp VoucherService).
// Chỉ liệt kê voucher còn dùng được; voucher chưa đủ điều kiện với giỏ hiện tại thì mờ đi kèm lý do.
export function VoucherPicker({
  vouchers,
  subtotal,
  shippingFee,
  shippingVoucherId,
  discountVoucherId,
  onChange,
}: {
  vouchers: Voucher[]
  subtotal: number
  // undefined khi chưa chọn tỉnh / đang báo giá
  shippingFee?: number
  shippingVoucherId: number | null
  discountVoucherId: number | null
  onChange: (patch: { shippingVoucherId?: number | null; discountVoucherId?: number | null }) => void
}) {
  const available = vouchers.filter((v) => v.status === 'AVAILABLE')
  const freeship = available.filter((v) => v.type === 'FREESHIP')
  const discounts = available.filter((v) => v.type !== 'FREESHIP')

  return (
    <section className="card p-5 sm:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="h2">Voucher</h2>
        <Link to="/vouchers" className="text-[13.5px] font-semibold">
          Ví voucher
        </Link>
      </div>
      <p className="mb-4 mt-1 text-[13.5px] text-ink-muted">
        Mỗi đơn dùng tối đa 1 voucher miễn phí vận chuyển và 1 voucher giảm giá.
      </p>
      {available.length === 0 ? (
        <p className="text-[14px] text-ink-muted">Bạn chưa có voucher nào dùng được.</p>
      ) : (
        <div className="space-y-5">
          <VoucherGroup
            title="Miễn phí vận chuyển"
            name="shipping-voucher"
            vouchers={freeship}
            subtotal={subtotal}
            selectedId={shippingVoucherId}
            saving={() => (shippingFee === undefined ? 'Miễn phí vận chuyển' : `−${formatPrice(shippingFee)} phí ship`)}
            onSelect={(id) => onChange({ shippingVoucherId: id })}
          />
          <VoucherGroup
            title="Giảm giá"
            name="discount-voucher"
            vouchers={discounts}
            subtotal={subtotal}
            selectedId={discountVoucherId}
            saving={(v) => `−${formatPrice(discountFor(v, subtotal))}`}
            onSelect={(id) => onChange({ discountVoucherId: id })}
          />
        </div>
      )}
    </section>
  )
}

function VoucherGroup({
  title,
  name,
  vouchers,
  subtotal,
  selectedId,
  saving,
  onSelect,
}: {
  title: string
  name: string
  vouchers: Voucher[]
  subtotal: number
  selectedId: number | null
  // số tiền được giảm nếu chọn voucher này, hiện cạnh nút chọn
  saving: (v: Voucher) => string
  onSelect: (id: number | null) => void
}) {
  if (vouchers.length === 0) return null
  return (
    <fieldset>
      <legend className="mb-2 text-[14px] font-bold">{title}</legend>
      <div className="space-y-2.5">
        {vouchers.map((v) => {
          const reason = ineligibleReason(v, subtotal)
          const checked = selectedId === v.id && !reason
          return (
            <label key={v.id} className={reason ? 'block cursor-not-allowed' : 'block cursor-pointer'}>
              <VoucherTicket
                voucher={v}
                dimmed={Boolean(reason)}
                aside={
                  <span className="flex items-center gap-2.5">
                    <span
                      className={`text-[12.5px] font-bold ${reason ? 'text-ink-muted' : 'text-success'}`}
                      aria-hidden={!reason}
                    >
                      {reason ?? saving(v)}
                    </span>
                    <input
                      type="radio"
                      name={name}
                      className="h-5 w-5 accent-primary"
                      checked={checked}
                      disabled={Boolean(reason)}
                      aria-label={`Dùng ${v.label}`}
                      onChange={() => onSelect(v.id)}
                    />
                  </span>
                }
              />
            </label>
          )
        })}
        <label className="flex cursor-pointer items-center gap-2.5 px-1 text-[13.5px] text-ink-2">
          <input
            type="radio"
            name={name}
            className="h-4 w-4 accent-primary"
            checked={selectedId === null}
            onChange={() => onSelect(null)}
          />
          Không dùng voucher {title.toLowerCase()}
        </label>
      </div>
    </fieldset>
  )
}

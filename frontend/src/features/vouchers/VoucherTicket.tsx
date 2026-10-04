import type { ReactNode } from 'react'
import type { Voucher } from '@/api/types'
import { formatDate } from '@/lib/format'
import { voucherCondition, VOUCHER_REASON } from './vouchers'

// voucher dạng vé: cuống màu bên trái theo loại, thân ghi điều kiện và hạn dùng
export function VoucherTicket({ voucher, aside, dimmed }: { voucher: Voucher; aside?: ReactNode; dimmed?: boolean }) {
  const freeship = voucher.type === 'FREESHIP'
  return (
    <div
      className={`flex overflow-hidden rounded-2xl border-2 bg-surface ${
        freeship ? 'border-[#BDE3F4]' : 'border-[#FBD3C9]'
      } ${dimmed ? 'opacity-60' : ''}`}
    >
      <div
        className={`grid w-[4.5rem] shrink-0 place-items-center border-r-2 border-dashed px-1.5 text-center font-display text-[12px] font-extrabold leading-tight text-white sm:w-24 sm:text-[13px] ${
          freeship ? 'border-[#BDE3F4] bg-sky' : 'border-[#FBD3C9] bg-coral'
        }`}
      >
        <span>
          <span className="block text-[24px]" aria-hidden>
            {freeship ? '🚚' : '🏷️'}
          </span>
          {freeship ? 'FREESHIP' : 'GIẢM GIÁ'}
        </span>
      </div>
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-2 p-3.5">
        <div className="min-w-[10rem] flex-1">
          <p className="font-display text-[17px] font-extrabold leading-tight">{voucher.label}</p>
          <p className="mt-0.5 text-[12.5px] text-ink-2">{voucherCondition(voucher)}</p>
          <p className="mt-0.5 text-[12px] text-ink-muted">
            {VOUCHER_REASON[voucher.reason]}
            {voucher.childName && ` · bé ${voucher.childName}`} · HSD {formatDate(voucher.expiresAt)}
          </p>
        </div>
        {aside}
      </div>
    </div>
  )
}

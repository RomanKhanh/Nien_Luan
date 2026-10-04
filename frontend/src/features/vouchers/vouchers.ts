import type { Voucher, VoucherReason, VoucherStatus } from '@/api/types'
import { formatPrice } from '@/lib/format'

export const voucherKey = ['vouchers'] as const

export const VOUCHER_REASON: Record<VoucherReason, string> = {
  WELCOME: 'Quà chào mừng',
  SKILL_BEGINNER: 'Mốc "Mới bắt đầu"',
  SKILL_DEVELOPING: 'Mốc "Đang phát triển"',
  SKILL_RICH: 'Mốc "Phong phú"',
}

export const VOUCHER_STATUS: Record<VoucherStatus, { label: string; tone: 'green' | 'gray' | 'red' }> = {
  AVAILABLE: { label: 'Dùng được', tone: 'green' },
  USED: { label: 'Đã dùng', tone: 'gray' },
  EXPIRED: { label: 'Hết hạn', tone: 'red' },
}

// "Đơn từ 200.000₫ · tối đa 150.000₫" / "Mọi đơn"
export function voucherCondition(v: Voucher): string {
  const parts = [v.minSubtotal > 0 ? `Tiền hàng từ ${formatPrice(v.minSubtotal)}` : 'Mọi đơn hàng']
  if (v.maxDiscount != null) parts.push(`giảm tối đa ${formatPrice(v.maxDiscount)}`)
  return parts.join(' · ')
}

// lý do voucher chưa dùng được cho đơn có tiền hàng này; null = dùng được
export function ineligibleReason(v: Voucher, subtotal: number): string | null {
  if (v.status === 'USED') return 'Đã dùng'
  if (v.status === 'EXPIRED') return 'Hết hạn'
  if (subtotal < v.minSubtotal) return `Cần thêm ${formatPrice(v.minSubtotal - subtotal)} tiền hàng`
  return null
}

// tiền giảm trên tiền hàng, khớp VoucherService.discountFor ở backend: % làm tròn xuống, không quá mức tối đa
// và không quá tiền hàng. Voucher freeship giảm đúng phí ship nên không tính ở đây.
export function discountFor(v: Voucher, subtotal: number): number {
  if (v.type === 'FREESHIP') return 0
  let discount = v.type === 'PERCENT_OFF' ? Math.floor((subtotal * (v.value ?? 0)) / 100) : (v.value ?? 0)
  if (v.maxDiscount != null) discount = Math.min(discount, v.maxDiscount)
  return Math.min(discount, subtotal)
}

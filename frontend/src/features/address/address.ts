import { z } from 'zod'
import type { Address } from '@/api/types'

// Giá trị của bộ chọn địa chỉ 3 cấp trong form (chuỗi để gắn thẳng vào react-hook-form).
// wardRequired = huyện đang chọn có cấp xã (huyện đảo thì không có, không bắt chọn phường/xã).
export interface AddressValue {
  provinceCode: string
  districtCode: string
  wardCode: string
  wardRequired: boolean
  addressDetail: string
}

export const EMPTY_ADDRESS: AddressValue = {
  provinceCode: '',
  districtCode: '',
  wardCode: '',
  wardRequired: true,
  addressDetail: '',
}

export function addressToValue(address: Address | null | undefined): AddressValue {
  if (!address) return EMPTY_ADDRESS
  return {
    provinceCode: String(address.provinceCode),
    districtCode: String(address.districtCode),
    wardCode: address.wardCode === null ? '' : String(address.wardCode),
    wardRequired: address.wardCode !== null,
    addressDetail: address.addressDetail,
  }
}

export const addressShape = {
  provinceCode: z.string(),
  districtCode: z.string(),
  wardCode: z.string(),
  wardRequired: z.boolean(),
  addressDetail: z.string().trim().max(255, 'Tối đa 255 ký tự'),
}

type AddressIssue = { path: (keyof AddressValue)[]; message: string }

// lỗi của địa chỉ 3 cấp; optional = cho phép bỏ trống toàn bộ (địa chỉ mặc định trong hồ sơ)
export function addressIssues(v: AddressValue, optional = false): AddressIssue[] {
  const blank = !v.provinceCode && !v.districtCode && !v.wardCode && !v.addressDetail.trim()
  if (optional && blank) return []
  const issues: AddressIssue[] = []
  if (!v.provinceCode) issues.push({ path: ['provinceCode'], message: 'Vui lòng chọn tỉnh/thành' })
  if (!v.districtCode) issues.push({ path: ['districtCode'], message: 'Vui lòng chọn quận/huyện' })
  if (v.wardRequired && !v.wardCode) issues.push({ path: ['wardCode'], message: 'Vui lòng chọn phường/xã' })
  if (!v.addressDetail.trim()) issues.push({ path: ['addressDetail'], message: 'Vui lòng nhập địa chỉ chi tiết' })
  return issues
}

// mã số gửi lên backend; chuỗi rỗng = null
export function codeOrNull(value: string): number | null {
  return value ? Number(value) : null
}

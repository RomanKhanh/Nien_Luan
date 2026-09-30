const vnd = new Intl.NumberFormat('vi-VN')

// 459000 -> "459.000₫"
export function formatPrice(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${vnd.format(Math.round(Number(value)))}₫`
}

// 4.6 -> "4,6"
export function formatDecimal(value: number, digits = 1): string {
  return value.toLocaleString('vi-VN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

// backend trả LocalDateTime / LocalDate không kèm múi giờ (giờ Việt Nam), parse như giờ địa phương
function parse(value: string): Date {
  return value.length === 10 ? new Date(`${value}T00:00:00`) : new Date(value)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  return parse(value).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return parse(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// "2 ngày trước"
export function formatRelative(value: string): string {
  const diffMs = Date.now() - parse(value).getTime()
  const minutes = Math.round(diffMs / 60000)
  if (minutes < 1) return 'vừa xong'
  if (minutes < 60) return `${minutes} phút trước`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} giờ trước`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days} ngày trước`
  if (days < 30) return `${Math.round(days / 7)} tuần trước`
  return formatDate(value)
}

export function ageRange(minAge: number, maxAge: number): string {
  return minAge === maxAge ? `${minAge} tuổi` : `${minAge}–${maxAge} tuổi`
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const letters = parts.length >= 2 ? parts[parts.length - 2][0] + parts[parts.length - 1][0] : parts[0].slice(0, 2)
  return letters.toUpperCase()
}

// các dòng hàng bị khiếu nại, vd "Robot lắp ráp × 2, Bộ xếp hình × 1"; rỗng = khiếu nại cả đơn
export function complaintItemsText(items: { productName: string; quantity: number }[]): string {
  return items.map((i) => `${i.productName} × ${i.quantity}`).join(', ')
}

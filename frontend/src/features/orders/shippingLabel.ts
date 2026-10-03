// "Phí vận chuyển (Miền Nam, 1 kiện)"; đơn cũ chưa có miền thì chỉ "Phí vận chuyển"
export function shippingLabel(zoneLabel: string | null | undefined, parcelCount: number | null | undefined): string {
  if (!zoneLabel) return 'Phí vận chuyển'
  return `Phí vận chuyển (${zoneLabel}${parcelCount ? `, ${parcelCount} kiện` : ''})`
}


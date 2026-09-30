import type { ComplaintStatus, ComplaintType, DocumentStatus, Gender, OrderStatus, PaymentMethod } from '@/api/types'

type Tone = 'gray' | 'blue' | 'amber' | 'violet' | 'green' | 'red'

// luồng trạng thái theo đề: Chờ xác nhận -> Đã xác nhận -> Đang giao -> Đã giao -> Đã huỷ
export const ORDER_STATUS: Record<OrderStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Chờ xác nhận', tone: 'amber' },
  CONFIRMED: { label: 'Đã xác nhận', tone: 'blue' },
  SHIPPING: { label: 'Đang giao', tone: 'violet' },
  DELIVERED: { label: 'Đã giao', tone: 'green' },
  CANCELLED: { label: 'Đã huỷ', tone: 'red' },
}

export const ORDER_FLOW: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED']

// khớp AdminOrderService.ALLOWED_TRANSITIONS ở backend
export const ORDER_NEXT: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}

export const PAYMENT_METHOD: Record<PaymentMethod, string> = {
  COD: 'Thanh toán khi nhận hàng (COD)',
  VNPAY: 'VNPay',
  MOMO: 'Ví MoMo',
}

export const COMPLAINT_TYPE: Record<ComplaintType, string> = {
  RETURN: 'Trả hàng',
  EXCHANGE: 'Đổi hàng',
  CANCEL: 'Yêu cầu huỷ đơn',
  QUALITY: 'Chất lượng sản phẩm',
  OTHER: 'Khác',
}

export const COMPLAINT_STATUS: Record<ComplaintStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Chờ tiếp nhận', tone: 'amber' },
  PROCESSING: { label: 'Đang xử lý', tone: 'blue' },
  RESOLVED: { label: 'Đã giải quyết', tone: 'green' },
  REJECTED: { label: 'Từ chối', tone: 'red' },
}

// loại yêu cầu được phép theo trạng thái đơn, khớp ComplaintService.ALLOWED_ORDER_STATUSES
export const COMPLAINT_ALLOWED: Record<ComplaintType, OrderStatus[]> = {
  RETURN: ['DELIVERED'],
  EXCHANGE: ['DELIVERED'],
  QUALITY: ['DELIVERED'],
  CANCEL: ['PENDING', 'CONFIRMED', 'SHIPPING'],
  OTHER: ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'],
}

export const GENDER: Record<Gender, string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
}

// trạng thái tài liệu trong cơ sở tri thức (enum DocumentStatus ở backend)
export const DOCUMENT_STATUS: Record<DocumentStatus, { label: string; tone: Tone }> = {
  UPLOADED: { label: 'Đã tải lên', tone: 'gray' },
  PROCESSING: { label: 'Đang xử lý', tone: 'amber' },
  INDEXED: { label: 'Đang dùng', tone: 'green' },
  DISABLED: { label: 'Đã tắt', tone: 'red' },
}

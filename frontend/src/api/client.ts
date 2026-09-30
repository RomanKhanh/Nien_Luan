import axios, { AxiosError } from 'axios'
import type { ApiEnvelope } from './types'
import { tokenStorage } from '@/auth/tokenStorage'

// dev dùng proxy của Vite (/api -> Spring Boot); build production đặt VITE_API_URL nếu backend ở domain khác
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = tokenStorage.get()?.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// token hết hạn / bị vô hiệu (đổi mật khẩu, bị khoá): xoá phiên, AuthProvider nghe sự kiện này để đăng xuất
http.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const hadToken = Boolean(error.config?.headers?.Authorization)
    if (error.response?.status === 401 && hadToken) {
      tokenStorage.clear()
      window.dispatchEvent(new Event('auth:expired'))
    }
    return Promise.reject(error)
  },
)

export class ApiError extends Error {
  readonly status: number
  readonly rawMessage: string

  constructor(status: number, rawMessage: string) {
    super(translateError(rawMessage, status))
    this.status = status
    this.rawMessage = rawMessage
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
    if (!error.response) return new ApiError(0, 'Network Error')
    return new ApiError(error.response.status, body?.message ?? error.message)
  }
  return new ApiError(-1, error instanceof Error ? error.message : String(error))
}

// gọi API và bóc lớp ApiResponse { success, message, data }
export async function request<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  try {
    const res = await promise
    return res.data.data
  } catch (error) {
    throw toApiError(error)
  }
}

// Backend trả thông báo lỗi tiếng Anh; dịch các câu hay gặp, câu lạ thì giữ nguyên
const EXACT: Record<string, string> = {
  'Network Error': 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.',
  'Incorrect email or password': 'Email hoặc mật khẩu không đúng.',
  'Account is locked': 'Tài khoản của bạn đang bị khoá. Vui lòng liên hệ hỗ trợ.',
  'Email already in use': 'Email này đã được đăng ký.',
  Unauthorized: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  'Access denied': 'Bạn không có quyền thực hiện thao tác này.',
  'Current password is incorrect': 'Mật khẩu hiện tại không đúng.',
  'New password must be different from current password': 'Mật khẩu mới phải khác mật khẩu hiện tại.',
  'Cart is empty!': 'Giỏ hàng đang trống.',
  'Only COD payment is supported at the moment': 'Hiện chỉ hỗ trợ thanh toán khi nhận hàng (COD).',
  'Product not found': 'Không tìm thấy sản phẩm.',
  'Order not found': 'Không tìm thấy đơn hàng.',
  'Product is already assigned to this child': 'Bé đã có sản phẩm này trong hồ sơ.',
  'You can only review products from delivered orders': 'Bạn chỉ đánh giá được sản phẩm trong đơn đã giao.',
  'You have already reviewed this product': 'Bạn đã đánh giá sản phẩm này rồi.',
  'A response to the customer is required to close the complaint':
    'Cần nhập phản hồi cho khách trước khi đóng yêu cầu.',
  'Category name already exists': 'Tên danh mục đã tồn tại.',
  'Category still has products, move them to another category first':
    'Danh mục còn sản phẩm, hãy chuyển sản phẩm sang danh mục khác trước.',
  'Skill code already exists': 'Mã nhóm kỹ năng đã tồn tại.',
  'Skill code cannot be changed': 'Không thể đổi mã nhóm kỹ năng.',
  'Skill is still used by products or child profiles': 'Nhóm kỹ năng đang được sản phẩm hoặc hồ sơ bé sử dụng.',
  'Cannot change status of an admin account': 'Không thể khoá tài khoản quản trị viên.',
  'minAge must not be greater than maxAge': 'Tuổi tối thiểu không được lớn hơn tuổi tối đa.',
  'Data conflicts with existing records': 'Dữ liệu bị trùng với bản ghi đã có.',
  'Internal server error': 'Máy chủ gặp lỗi, vui lòng thử lại sau.',
}

const PREFIX: [string, string][] = [
  ['Not enough stock for: ', 'Không đủ hàng trong kho cho: '],
  ['Insufficient stock for: ', 'Không đủ hàng trong kho cho: '],
  ['Product is no longer available: ', 'Sản phẩm đã ngừng bán: '],
  ["Can't cancel this order because it is already ", 'Không thể huỷ đơn ở trạng thái '],
  ['Cannot change order status from ', 'Không thể chuyển trạng thái đơn từ '],
  ['Quantity per product cannot exceed ', 'Mỗi sản phẩm tối đa '],
]

function translateError(message: string, status: number): string {
  if (EXACT[message]) return EXACT[message]
  for (const [en, vi] of PREFIX) {
    if (message.startsWith(en)) return vi + message.slice(en.length)
  }
  if (status >= 500) return EXACT['Internal server error']
  return message
}

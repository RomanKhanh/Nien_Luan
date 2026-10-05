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
  'Payment gateway is not available right now, please try again later':
    'Cổng thanh toán MoMo đang không khả dụng, vui lòng thử lại sau.',
  'This order has already been paid': 'Đơn hàng này đã được thanh toán.',
  'Cannot pay for a cancelled order': 'Không thể thanh toán đơn đã huỷ.',
  'Password reset link is invalid or has expired': 'Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.',
  'Verification code is incorrect': 'Mã xác nhận không đúng.',
  'Verification code is invalid or has expired': 'Mã xác nhận không hợp lệ hoặc đã hết hạn, hãy gửi mã mới.',
  'Too many wrong codes, please request a new code': 'Nhập sai mã quá nhiều lần, hãy gửi mã mới.',
  'Please wait before requesting a new code': 'Vui lòng đợi một chút rồi mới gửi lại mã.',
  'Email sending is not configured': 'Hệ thống chưa cấu hình gửi email nên chưa gửi được thư. Vui lòng liên hệ quản trị viên.',
  'Please wait before requesting a new reset link': 'Bạn vừa yêu cầu link, vui lòng đợi 1 phút rồi gửi lại.',
  'The payment deadline for this order has passed': 'Đơn đã quá hạn thanh toán và sẽ tự huỷ.',
  'This order is not set up for online payment (COD)': 'Đơn này thanh toán khi nhận hàng, không thanh toán online.',
  'Product not found': 'Không tìm thấy sản phẩm.',
  'Order not found': 'Không tìm thấy đơn hàng.',
  'Product is already assigned to this child': 'Bé đã có sản phẩm này trong hồ sơ.',
  'You can only review products from delivered orders': 'Bạn chỉ đánh giá được sản phẩm trong đơn đã giao.',
  'You have already reviewed this product': 'Bạn đã đánh giá sản phẩm này rồi.',
  'A response to the customer is required to close the complaint':
    'Cần nhập phản hồi cho khách trước khi đóng yêu cầu.',
  'Complaint not found': 'Không tìm thấy yêu cầu.',
  'File is empty': 'Tệp ảnh trống.',
  'File has no extension': 'Tệp không có đuôi mở rộng.',
  'Only jpg, jpeg, png, webp files are allowed': 'Chỉ nhận ảnh JPG, PNG hoặc WebP.',
  'File content does not match its extension': 'Nội dung tệp không phải ảnh đúng định dạng.',
  'A product can have at most 10 images': 'Mỗi sản phẩm tối đa 10 ảnh.',
  'Maximum upload size exceeded': 'Tệp vượt quá dung lượng cho phép (tổng tối đa 200MB mỗi lần gửi).',
  'Product images must be at most 5MB': 'Ảnh sản phẩm tối đa 5MB.',
  'An unboxing video is required for exchange and return requests':
    'Cần đính kèm video mở hàng cho yêu cầu đổi hoặc trả hàng.',
  'The unboxing video must be an mp4, mov or webm file': 'Video mở hàng phải là tệp MP4, MOV hoặc WebM.',
  'Evidence must be a jpg, png, webp image or an mp4, mov, webm video':
    'Ảnh / video tình trạng phải là ảnh JPG, PNG, WebP hoặc video MP4, MOV, WebM.',
  'Evidence file is empty': 'Tệp đính kèm bị trống.',
  'At most 5 condition photos/videos': 'Tối đa 5 ảnh / video tình trạng sản phẩm.',
  'Attachment not found': 'Không tìm thấy tệp đính kèm.',
  'Attachment has been deleted': 'Tệp đã được xóa tự động sau thời hạn lưu trữ.',
  'Image not found for this product': 'Không tìm thấy ảnh của sản phẩm này.',
  'Category name already exists': 'Tên danh mục đã tồn tại.',
  'Category still has products, move them to another category first':
    'Danh mục còn sản phẩm, hãy chuyển sản phẩm sang danh mục khác trước.',
  'Skill code already exists': 'Mã nhóm kỹ năng đã tồn tại.',
  'Skill code cannot be changed': 'Không thể đổi mã nhóm kỹ năng.',
  'Skill is still used by products or child profiles': 'Nhóm kỹ năng đang được sản phẩm hoặc hồ sơ bé sử dụng.',
  'Cannot change status of an admin account': 'Không thể khoá tài khoản quản trị viên.',
  'minAge must not be greater than maxAge': 'Tuổi tối thiểu không được lớn hơn tuổi tối đa.',
  'Data conflicts with existing records': 'Dữ liệu bị trùng với bản ghi đã có.',
  'Province code is invalid': 'Tỉnh/thành không hợp lệ, vui lòng chọn lại.',
  'District code is invalid': 'Quận/huyện không hợp lệ, vui lòng chọn lại.',
  'Ward code is invalid': 'Phường/xã không hợp lệ, vui lòng chọn lại.',
  'District does not belong to the selected province': 'Quận/huyện không thuộc tỉnh/thành đã chọn.',
  'Ward does not belong to the selected district': 'Phường/xã không thuộc quận/huyện đã chọn.',
  'Ward is required': 'Vui lòng chọn phường/xã.',
  'This district has no wards': 'Quận/huyện này không có cấp phường/xã.',
  'Address detail is required': 'Vui lòng nhập địa chỉ chi tiết (số nhà, tên đường).',
  'Province not found': 'Không tìm thấy tỉnh/thành.',
  'District not found': 'Không tìm thấy quận/huyện.',
  'Cannot send feedback for an order you cancelled yourself': 'Đơn bạn đã tự huỷ nên không gửi phản hồi được.',
  'Internal server error': 'Máy chủ gặp lỗi, vui lòng thử lại sau.',
}

const PREFIX: [string, string][] = [
  ['Not enough stock for: ', 'Không đủ hàng trong kho cho: '],
  ['Insufficient stock for: ', 'Không đủ hàng trong kho cho: '],
  ['Product is no longer available: ', 'Sản phẩm đã ngừng bán: '],
  ["Can't cancel this order because it is already ", 'Không thể huỷ đơn ở trạng thái '],
  ['Cannot change order status from ', 'Không thể chuyển trạng thái đơn từ '],
  ['Quantity per product cannot exceed ', 'Mỗi sản phẩm tối đa '],
  ['Cannot change complaint status from ', 'Không thể chuyển trạng thái yêu cầu từ '],
  ['File content does not match its extension: ', 'Nội dung tệp không đúng định dạng: '],
  ['File is too large: ', 'Tệp quá lớn: '],
  ['Duplicate orderItemId: ', 'Sản phẩm bị chọn trùng trong yêu cầu, mã dòng hàng: '],
  ['Order item does not belong to this order: ', 'Sản phẩm không thuộc đơn hàng này, mã dòng hàng: '],
  [
    'Quantity complained exceeds purchased quantity for order item ',
    'Số lượng khiếu nại vượt quá số lượng đã mua, mã dòng hàng: ',
  ],
]

function translateError(message: string, status: number): string {
  if (EXACT[message]) return EXACT[message]
  for (const [en, vi] of PREFIX) {
    if (message.startsWith(en)) return vi + message.slice(en.length)
  }
  if (status >= 500) return EXACT['Internal server error']
  return message
}

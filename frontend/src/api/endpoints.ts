import { ApiError, http, request, toApiError } from './client'
import type {
  AdminStats,
  AppNotification,
  Cart,
  Category,
  ChildProduct,
  ChildProfile,
  ChildProfileRequest,
  Complaint,
  ComplaintStatus,
  ComplaintType,
  CreateOrderRequest,
  DistrictOption,
  LoginResponse,
  LocationOption,
  NotificationType,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PaymentInfo,
  ProductDetail,
  ProductImage,
  ProductMatch,
  ProductQuery,
  ProductRequest,
  ProductSummary,
  ProvinceOption,
  Recommendation,
  Review,
  SearchSuggestion,
  ShippingQuote,
  Skill,
  SkillBundlePreview,
  SkillPreview,
  SkillProfile,
  SkillTimeline,
  UnreadCount,
  UpdateProfileRequest,
  UserProfile,
} from './types'

// axios tự bỏ tham số undefined; mảng skills gửi dạng skills=LOGIC,STEM cho khớp @RequestParam List<String>
function productParams(q: ProductQuery) {
  return { ...q, skills: q.skills && q.skills.length > 0 ? q.skills.join(',') : undefined }
}

export const authApi = {
  login: (email: string, password: string) => request<LoginResponse>(http.post('/auth/login', { email, password })),
  sendVerificationCode: (email: string) => request<unknown>(http.post('/auth/register/send-code', { email })),
  register: (body: { fullName: string; email: string; password: string; verificationCode: string }) =>
    request<unknown>(http.post('/auth/register', body)),
  forgotPassword: (email: string) => request<unknown>(http.post('/auth/forgot-password', { email })),
  resetPassword: (token: string, newPassword: string) =>
    request<unknown>(http.post('/auth/reset-password', { token, newPassword })),
}

export const meApi = {
  get: () => request<UserProfile>(http.get('/me')),
  update: (body: UpdateProfileRequest) => request<UserProfile>(http.put('/me', body)),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    request<void>(http.put('/me/password', body)),
}

// địa chính 63 tỉnh/thành trước sáp nhập; dữ liệu cố định nên cache lâu ở react-query
export const locationApi = {
  provinces: () => request<ProvinceOption[]>(http.get('/locations/provinces')),
  districts: (provinceCode: number) =>
    request<DistrictOption[]>(http.get(`/locations/provinces/${provinceCode}/districts`)),
  wards: (districtCode: number) => request<LocationOption[]>(http.get(`/locations/districts/${districtCode}/wards`)),
}

export const shippingApi = {
  // phí vận chuyển giỏ hàng hiện tại tới tỉnh đã chọn
  quote: (provinceCode: number) => request<ShippingQuote>(http.post('/shipping/quote', { provinceCode })),
}

export const catalogApi = {
  products: (q: ProductQuery) => request<Page<ProductSummary>>(http.get('/products', { params: productParams(q) })),
  product: (id: number) => request<ProductDetail>(http.get(`/products/${id}`)),
  suggestions: (q: string) => request<SearchSuggestion[]>(http.get('/products/suggestions', { params: { q } })),
  reviews: (id: number, page = 0, size = 5) =>
    request<Page<Review>>(http.get(`/products/${id}/reviews`, { params: { page, size } })),
  createReview: (id: number, body: { rating: number; comment: string }) =>
    request<Review>(http.post(`/products/${id}/reviews`, body)),
  categories: () => request<Category[]>(http.get('/categories')),
  skills: () => request<Skill[]>(http.get('/skills')),
}

export const cartApi = {
  get: () => request<Cart>(http.get('/cart')),
  add: (productId: number, quantity: number) => request<Cart>(http.post('/cart/items', { productId, quantity })),
  update: (itemId: number, quantity: number) => request<Cart>(http.patch(`/cart/items/${itemId}`, { quantity })),
  remove: (itemId: number) => request<Cart>(http.delete(`/cart/items/${itemId}`)),
  clear: () => request<void>(http.delete('/cart')),
}

export const orderApi = {
  create: (body: CreateOrderRequest) => request<Order>(http.post('/orders', body)),
  list: () => request<Order[]>(http.get('/orders')),
  get: (id: number) => request<Order>(http.get(`/orders/${id}`)),
  cancel: (id: number) => request<Order>(http.patch(`/orders/${id}/cancel`)),
}

// thanh toán online (MoMo): backend tạo link, khách thanh toán trên trang MoMo rồi được chuyển về /payment/momo-return
export const paymentApi = {
  createUrl: (orderId: number) =>
    request<{ paymentUrl: string }>(http.post(`/payments/${orderId}/create-url`)).then((r) => r.paymentUrl),
  // đơn chưa từng bấm thanh toán thì chưa có bản ghi Payment (backend trả 404) -> null
  status: async (orderId: number): Promise<PaymentInfo | null> => {
    try {
      return await request<PaymentInfo>(http.get(`/payments/${orderId}`))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null
      throw e
    }
  },
  // kiểm tra chữ ký các tham số MoMo gắn vào URL khi chuyển khách về. Endpoint này luôn trả HTTP 200,
  // kết quả nằm ở cờ success. Chỉ để hiển thị: xác nhận thật do MoMo gọi IPN vào server.
  verifyMomoReturn: async (params: Record<string, string>): Promise<boolean> => {
    try {
      const res = await http.get('/payments/momo-return', { params })
      return Boolean(res.data?.success)
    } catch (e) {
      throw toApiError(e)
    }
  },
}

export const childApi = {
  list: () => request<ChildProfile[]>(http.get('/children')),
  get: (id: number) => request<ChildProfile>(http.get(`/children/${id}`)),
  create: (body: ChildProfileRequest) => request<ChildProfile>(http.post('/children', body)),
  update: (id: number, body: ChildProfileRequest) => request<ChildProfile>(http.put(`/children/${id}`, body)),
  remove: (id: number) => request<void>(http.delete(`/children/${id}`)),
  products: (id: number) => request<ChildProduct[]>(http.get(`/children/${id}/products`)),
  assignProduct: (id: number, productId: number) =>
    request<ChildProduct>(http.post(`/children/${id}/products`, { productId })),
  removeProduct: (id: number, childProductId: number) =>
    request<void>(http.delete(`/children/${id}/products/${childProductId}`)),
  skillProfile: (id: number) => request<SkillProfile>(http.get(`/children/${id}/skill-profile`)),
  timeline: (id: number) => request<SkillTimeline>(http.get(`/children/${id}/skill-profile/timeline`)),
  // dự kiến điểm kỹ năng của bé nếu thêm sản phẩm productId
  skillPreview: (id: number, productId: number) =>
    request<SkillPreview>(http.get(`/children/${id}/skill-profile/preview`, { params: { productId } })),
  // dự kiến hồ sơ nếu bé nhận cùng lúc nhiều món (vd các món gán cho bé lúc thanh toán)
  skillPreviewBundle: (id: number, productIds: number[]) =>
    request<SkillBundlePreview>(
      http.get(`/children/${id}/skill-profile/preview-bundle`, { params: { productIds: productIds.join(',') } }),
    ),
  // "Hợp với bé": cùng bộ lọc với danh sách sản phẩm, độ tuổi luôn theo tuổi bé (backend bỏ qua ageFrom/ageTo)
  productMatches: (id: number, q: ProductQuery) =>
    request<Page<ProductMatch>>(
      http.get(`/children/${id}/product-matches`, {
        params: { ...productParams(q), age: undefined, ageFrom: undefined, ageTo: undefined },
      }),
    ),
  recommendations: (id: number, limit = 4) =>
    request<Recommendation[]>(http.get(`/children/${id}/skill-profile/recommendations`, { params: { limit } })),
}

// thông báo của chính người đang đăng nhập (khách hàng hoặc admin)
export const notificationApi = {
  list: (page = 0, size = 10) => request<Page<AppNotification>>(http.get('/notifications', { params: { page, size } })),
  unreadCount: () => request<UnreadCount>(http.get('/notifications/unread-count')),
  markRead: (id: number) => request<void>(http.patch(`/notifications/${id}/read`)),
  // types rỗng = tất cả
  markAllRead: (types: NotificationType[] = []) =>
    request<number>(
      http.patch('/notifications/read-all', null, { params: { types: types.length ? types.join(',') : undefined } }),
    ),
}

export const feedbackApi = {
  myReviews: () => request<Review[]>(http.get('/reviews/mine')),
  myComplaints: () => request<Complaint[]>(http.get('/complaints')),
  // items rỗng = khiếu nại chung cả đơn
  // có file bằng chứng thì gửi multipart: part "request" (JSON) + "unboxingVideo" + "conditionFiles";
  // onProgress nhận tỉ lệ 0..1 để hiện thanh tải lên (video có thể lớn)
  createComplaint: (
    orderId: number,
    body: { type: ComplaintType; content: string; items: { orderItemId: number; quantity: number }[] },
    evidence?: { unboxingVideo?: File; conditionFiles: File[] },
    onProgress?: (ratio: number) => void,
  ) => {
    if (!evidence?.unboxingVideo && !evidence?.conditionFiles.length) {
      return request<Complaint>(http.post(`/orders/${orderId}/complaints`, body))
    }
    const form = new FormData()
    form.append('request', new Blob([JSON.stringify(body)], { type: 'application/json' }))
    if (evidence.unboxingVideo) form.append('unboxingVideo', evidence.unboxingVideo)
    evidence.conditionFiles.forEach((file) => form.append('conditionFiles', file))
    return request<Complaint>(
      http.post(`/orders/${orderId}/complaints`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (e) => e.total && onProgress?.(e.loaded / e.total),
      }),
    )
  },
  // file bằng chứng là dữ liệu riêng tư: tải kèm token rồi hiển thị qua object URL (thẻ <video src> không gửi được token)
  complaintAttachment: async (complaintId: number, attachmentId: number) => {
    try {
      const res = await http.get<Blob>(`/complaints/${complaintId}/attachments/${attachmentId}`, {
        responseType: 'blob',
      })
      return res.data
    } catch (e) {
      throw toApiError(e)
    }
  },
}

export const adminApi = {
  stats: () => request<AdminStats>(http.get('/admin/stats')),

  products: (params: { keyword?: string; categoryId?: number; page?: number; size?: number }) =>
    request<Page<ProductSummary>>(http.get('/admin/products', { params })),
  product: (id: number) => request<ProductDetail>(http.get(`/admin/products/${id}`)),
  createProduct: (body: ProductRequest) => request<ProductDetail>(http.post('/admin/products', body)),
  updateProduct: (id: number, body: ProductRequest) => request<ProductDetail>(http.put(`/admin/products/${id}`, body)),
  updateStock: (id: number, stockQuantity: number) =>
    request<ProductDetail>(http.patch(`/admin/products/${id}/stock`, { stockQuantity })),
  hideProduct: (id: number) => request<void>(http.delete(`/admin/products/${id}`)),

  // ảnh sản phẩm: mỗi thao tác lưu ngay, tách khỏi PUT sản phẩm
  uploadProductImage: (productId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    // ghi đè Content-Type mặc định (JSON) của http, nếu không axios sẽ chuyển FormData thành JSON
    return request<ProductImage>(
      http.post(`/admin/products/${productId}/images`, form, { headers: { 'Content-Type': 'multipart/form-data' } }),
    )
  },
  deleteProductImage: (productId: number, imageId: number) =>
    request<void>(http.delete(`/admin/products/${productId}/images/${imageId}`)),
  setProductThumbnail: (productId: number, imageId: number) =>
    request<ProductImage>(http.patch(`/admin/products/${productId}/images/${imageId}/thumbnail`)),
  reorderProductImages: (productId: number, imageIds: number[]) =>
    request<ProductImage[]>(http.put(`/admin/products/${productId}/images/order`, { imageIds })),

  createCategory: (body: { name: string; description: string }) =>
    request<Category>(http.post('/admin/categories', body)),
  updateCategory: (id: number, body: { name: string; description: string }) =>
    request<Category>(http.put(`/admin/categories/${id}`, body)),
  deleteCategory: (id: number) => request<void>(http.delete(`/admin/categories/${id}`)),
  createSkill: (body: { code: string; name: string; description: string }) =>
    request<Skill>(http.post('/admin/skills', body)),
  updateSkill: (id: number, body: { code: string; name: string; description: string }) =>
    request<Skill>(http.put(`/admin/skills/${id}`, body)),
  deleteSkill: (id: number) => request<void>(http.delete(`/admin/skills/${id}`)),

  orders: (params: { status?: OrderStatus; page?: number; size?: number }) =>
    request<Page<OrderSummary>>(http.get('/admin/orders', { params })),
  order: (id: number) => request<Order>(http.get(`/admin/orders/${id}`)),
  updateOrderStatus: (id: number, status: OrderStatus) =>
    request<Order>(http.patch(`/admin/orders/${id}/status`, { status })),

  users: (params: { keyword?: string; page?: number; size?: number }) =>
    request<Page<UserProfile>>(http.get('/admin/users', { params })),
  user: (id: number) => request<UserProfile>(http.get(`/admin/users/${id}`)),
  updateUserStatus: (id: number, enabled: boolean) =>
    request<UserProfile>(http.patch(`/admin/users/${id}/status`, { enabled })),

  reviews: (params: { visible?: boolean; page?: number; size?: number }) =>
    request<Page<Review>>(http.get('/admin/reviews', { params })),
  setReviewVisibility: (id: number, visible: boolean) =>
    request<Review>(http.patch(`/admin/reviews/${id}/visibility`, { visible })),

  complaints: (params: { status?: ComplaintStatus; page?: number; size?: number }) =>
    request<Page<Complaint>>(http.get('/admin/complaints', { params })),
  handleComplaint: (id: number, body: { status: ComplaintStatus; response: string }) =>
    request<Complaint>(http.patch(`/admin/complaints/${id}`, body)),
  // giữ lại bằng chứng (không tự xóa sau 30 ngày) / bỏ giữ
  setEvidenceHold: (id: number, hold: boolean) =>
    request<Complaint>(http.patch(`/admin/complaints/${id}/evidence-hold`, { hold })),
}

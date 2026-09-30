import { http, request } from './client'
import type {
  AdminStats,
  Cart,
  Category,
  ChildProduct,
  ChildProfile,
  ChildProfileRequest,
  Complaint,
  ComplaintStatus,
  ComplaintType,
  CreateOrderRequest,
  LoginResponse,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  ProductDetail,
  ProductQuery,
  ProductRequest,
  ProductSummary,
  Recommendation,
  Review,
  Skill,
  SkillProfile,
  SkillTimeline,
  UserProfile,
} from './types'

// axios tự bỏ tham số undefined; mảng skills gửi dạng skills=LOGIC,STEM cho khớp @RequestParam List<String>
function productParams(q: ProductQuery) {
  return { ...q, skills: q.skills && q.skills.length > 0 ? q.skills.join(',') : undefined }
}

export const authApi = {
  login: (email: string, password: string) => request<LoginResponse>(http.post('/auth/login', { email, password })),
  register: (body: { fullName: string; email: string; password: string }) =>
    request<unknown>(http.post('/auth/register', body)),
}

export const meApi = {
  get: () => request<UserProfile>(http.get('/me')),
  update: (body: { fullName: string; phone: string; defaultAddress: string }) =>
    request<UserProfile>(http.put('/me', body)),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    request<void>(http.put('/me/password', body)),
}

export const catalogApi = {
  products: (q: ProductQuery) => request<Page<ProductSummary>>(http.get('/products', { params: productParams(q) })),
  product: (id: number) => request<ProductDetail>(http.get(`/products/${id}`)),
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
  recommendations: (id: number, limit = 4) =>
    request<Recommendation[]>(http.get(`/children/${id}/skill-profile/recommendations`, { params: { limit } })),
}

export const feedbackApi = {
  myReviews: () => request<Review[]>(http.get('/reviews/mine')),
  myComplaints: () => request<Complaint[]>(http.get('/complaints')),
  createComplaint: (body: { orderId: number; orderItemId: number | null; type: ComplaintType; content: string }) =>
    request<Complaint>(http.post('/complaints', body)),
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
}

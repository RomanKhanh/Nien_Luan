// Kiểu dữ liệu khớp với DTO của backend (com.brainblocks.backend.dto)

export interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Role = 'CUSTOMER' | 'ADMIN'

export interface LoginResponse {
  token: string
  role: Role
  userId: number
}

export interface UserProfile {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: Role
  enabled: boolean
  defaultAddress: string | null
  createdAt: string
}

// ===== Sản phẩm =====

export interface Skill {
  id: number
  code: string
  name: string
  description?: string | null
}

export interface SkillImpact {
  skillId: number
  skillCode: string
  skillName: string
  impactIndex: number
}

export interface Category {
  id: number
  name: string
  description: string | null
  productCount: number
}

export interface ProductSummary {
  id: number
  name: string
  price: number
  minAge: number
  maxAge: number
  stockQuantity: number
  active: boolean
  thumbnailUrl: string | null
  categoryId: number
  categoryName: string
  skillImpacts: SkillImpact[]
  averageRating: number
  reviewCount: number
}

export interface ProductImage {
  id: number
  url: string
  displayOrder: number
  thumbnail: boolean
}

export interface ProductDetail extends Omit<ProductSummary, 'thumbnailUrl'> {
  description: string | null
  images: ProductImage[]
  ratingBreakdown: Record<string, number>
  soldCount: number
  createdAt: string
}

export type ProductSort = 'relevance' | 'price_asc' | 'price_desc' | 'newest' | 'name'

export interface ProductQuery {
  keyword?: string
  categoryId?: number
  age?: number
  ageFrom?: number
  ageTo?: number
  skills?: string[]
  minPrice?: number
  maxPrice?: number
  inStock?: boolean
  sort?: ProductSort
  page?: number
  size?: number
}

export interface Review {
  id: number
  productId: number
  productName: string
  customerName: string
  rating: number
  comment: string | null
  visible: boolean
  createdAt: string
}

// ===== Giỏ hàng / đơn hàng =====

export interface CartItem {
  id: number
  productId: number
  productName: string
  thumbnailUrl: string | null
  price: number
  quantity: number
  subtotal: number
  stockQuantity: number
  active: boolean
}

export interface Cart {
  id: number
  items: CartItem[]
  totalAmount: number
}

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED'
export type PaymentMethod = 'COD' | 'VNPAY' | 'MOMO'

export interface OrderItem {
  id: number
  productId: number
  productName: string
  quantity: number
  unitPrice: number
  subtotal: number
  childProfileId: number | null
  childName: string | null
}

export interface Order {
  id: number
  orderCode: string
  receiverName: string
  receiverPhone: string
  shippingAddress: string
  totalAmount: number
  status: OrderStatus
  paymentMethod: PaymentMethod
  items: OrderItem[]
  createdAt: string
}

export interface OrderSummary {
  id: number
  orderCode: string
  customerName: string
  customerEmail: string
  totalAmount: number
  status: OrderStatus
  paymentMethod: PaymentMethod
  createdAt: string
}

export interface CreateOrderRequest {
  receiverName: string
  receiverPhone: string
  shippingAddress: string
  paymentMethod: PaymentMethod
  childAssignments: { productId: number; childProfileId: number }[]
}

// ===== Hồ sơ bé / kỹ năng =====

export type Gender = 'MALE' | 'FEMALE' | 'OTHER'

export interface ChildProfile {
  id: number
  name: string
  birthDate: string
  age: number
  gender: Gender | null
  note: string | null
  interestedSkills: Skill[]
  createdAt: string
}

export interface ChildProfileRequest {
  name: string
  birthDate: string
  gender: Gender | null
  note: string | null
  interestedSkillIds: number[]
}

export interface ChildProduct {
  id: number
  productId: number
  productName: string
  price: number
  source: 'PURCHASED' | 'MANUAL'
  addedAt: string
}

export interface SkillScore {
  skillId: number
  skillCode: string
  skillName: string
  score: number
  percentage: number
}

export interface SkillProfile {
  childProfileId: number
  totalProducts: number
  updatedAt: string | null
  skillScores: SkillScore[]
  strongestSkill: SkillScore | null
  weakestSkill: SkillScore | null
}

export interface SkillTimelinePoint {
  childProductId: number
  productId: number
  productName: string
  source: 'PURCHASED' | 'MANUAL'
  addedAt: string
  totalProducts: number
  skillScores: SkillScore[]
}

export interface SkillTimeline {
  childProfileId: number
  points: SkillTimelinePoint[]
}

export interface Recommendation {
  productId: number
  name: string
  price: number
  minAge: number
  maxAge: number
  thumbnailUrl: string | null
  score: number
  reasons: string[]
}

// ===== Khiếu nại =====

export type ComplaintType = 'RETURN' | 'EXCHANGE' | 'CANCEL' | 'QUALITY' | 'OTHER'
export type ComplaintStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED'

export interface Complaint {
  id: number
  orderId: number
  orderCode: string
  orderStatus: OrderStatus
  orderItemId: number | null
  productName: string | null
  customerName: string
  customerEmail: string
  type: ComplaintType
  content: string
  status: ComplaintStatus
  response: string | null
  handledByName: string | null
  createdAt: string
  handledAt: string | null
}

// ===== Admin =====

export interface ProductRequest {
  name: string
  description: string | null
  price: number
  stockQuantity: number
  minAge: number
  maxAge: number
  categoryId: number
  imageUrls: string[]
  skillImpacts: { skillId: number; impactIndex: number }[]
  active: boolean
}

export interface AdminStats {
  totalCustomers: number
  newCustomersLast30Days: number
  activeProducts: number
  lowStockProducts: number
  totalOrders: number
  ordersByStatus: Record<OrderStatus, number>
  deliveredRevenue: number
  dailyOrders: { date: string; orders: number; amount: number }[]
  topProducts: { productId: number; productName: string; quantity: number; revenue: number }[]
  skillInterests: { skillId: number; skillName: string; children: number }[]
  pendingComplaints: number
  chatQuestions: number
  chatTopics: { topic: string; total: number }[]
}

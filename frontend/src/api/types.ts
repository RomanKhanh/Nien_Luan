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
  videoUrl: string | null
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
// khớp enum PaymentMethod ở backend (VNPay chưa tích hợp nên không có)
export type PaymentMethod = 'COD' | 'MOMO'
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED'

// GET /api/payments/{orderId}
export interface PaymentInfo {
  orderId: number
  status: PaymentStatus
  amount: number
  transactionId: string | null
  paidAt: string | null
}

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
  skillGains: SkillGain[]
}

// điểm một nhóm kỹ năng của bé hiện tại và nếu thêm sản phẩm (thang 0-10)
export interface SkillGain {
  skillId: number
  skillCode: string
  skillName: string
  currentScore: number
  projectedScore: number
  gain: number
}

export interface SkillPreview {
  childProfileId: number
  productId: number
  alreadyOwned: boolean
  gains: SkillGain[]
}

// ===== Khiếu nại =====

export type ComplaintType = 'RETURN' | 'EXCHANGE' | 'CANCEL' | 'QUALITY' | 'OTHER'
export type ComplaintStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'REJECTED'

// dòng hàng bị khiếu nại kèm số lượng lỗi
export interface ComplaintItem {
  orderItemId: number
  productId: number
  productName: string
  quantity: number
}

export interface Complaint {
  id: number
  orderId: number
  orderCode: string
  orderStatus: OrderStatus
  customerName: string
  customerEmail: string
  type: ComplaintType
  content: string
  status: ComplaintStatus
  response: string | null
  handledByName: string | null
  // rỗng = khiếu nại chung cả đơn
  items: ComplaintItem[]
  createdAt: string
  handledAt: string | null
}

// ===== Admin =====

export interface ProductRequest {
  name: string
  description: string | null
  videoUrl: string | null
  price: number
  stockQuantity: number
  minAge: number
  maxAge: number
  categoryId: number
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

// ===== Chatbot AI & cơ sở tri thức (RAG) =====
// Khớp các entity ChatSession / ChatMessage / ChatbotConfig / KnowledgeDocument / DocumentChunk ở backend.
// Hợp đồng API đầy đủ: xem docs/chatbot-api.md

export type ChatSender = 'USER' | 'BOT'

// sản phẩm chatbot gợi ý trong một tin nhắn BOT (ChatMessage.suggestedProducts) kèm lý do gợi ý
export interface ChatSuggestedProduct {
  productId: number
  name: string
  price: number
  minAge: number
  maxAge: number
  thumbnailUrl: string | null
  skillImpacts: SkillImpact[]
  reason: string | null
}

// đoạn tài liệu trong cơ sở tri thức được dùng làm căn cứ cho câu trả lời
export interface ChatSource {
  documentId: number
  documentTitle: string
  snippet: string
}

export interface ChatMessage {
  id: number
  sender: ChatSender
  content: string
  topic: string | null
  createdAt: string
  responseTimeMs: number
  suggestedProducts: ChatSuggestedProduct[]
  sources: ChatSource[]
}

export interface ChatSessionSummary {
  id: number
  title: string | null
  startedAt: string
  lastMessageAt: string | null
  messageCount: number
  childProfileId: number | null
  childName: string | null
}

export interface ChatSessionDetail extends ChatSessionSummary {
  messages: ChatMessage[]
}

// trả về sau khi gửi 1 câu hỏi: tin nhắn của người dùng đã lưu + câu trả lời của bot
export interface ChatReply {
  userMessage: ChatMessage
  botMessage: ChatMessage
}

export interface AdminChatSessionSummary extends ChatSessionSummary {
  customerId: number
  customerName: string
  customerEmail: string
}

export interface AdminChatSessionDetail extends AdminChatSessionSummary {
  messages: ChatMessage[]
}

export interface ChatStats {
  totalSessions: number
  totalQuestions: number
  questionsLast7Days: number
  avgResponseTimeMs: number
  topTopics: { topic: string; total: number }[]
  topQuestions: { question: string; total: number }[]
  topSuggestedProducts: { productId: number; productName: string; total: number }[]
  dailyQuestions: { date: string; total: number }[]
}

export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'INDEXED' | 'DISABLED'

export interface KnowledgeDocument {
  id: number
  title: string
  fileUrl: string
  fileType: string
  status: DocumentStatus
  uploadedAt: string
  uploadedByName: string
  chunkCount: number
}

export interface DocumentChunk {
  id: number
  chunkIndex: number
  content: string
  embedded: boolean
}

export interface ChatbotConfig {
  id: number
  modelName: string
  temperature: number
  maxTokens: number
  topK: number
  systemPrompt: string | null
  active: boolean
  updatedAt: string
  adminName: string
}

export type ChatbotConfigRequest = Pick<
  ChatbotConfig,
  'modelName' | 'temperature' | 'maxTokens' | 'topK' | 'systemPrompt'
>

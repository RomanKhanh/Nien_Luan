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
  // địa chỉ mặc định để hiển thị (3 cấp nếu có, không thì chuỗi địa chỉ cũ của tài khoản)
  defaultAddress: string | null
  // địa chỉ mặc định 3 cấp, tự điền ở trang thanh toán
  defaultShippingAddress: Address | null
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

// gợi ý dưới ô tìm kiếm; label là tên gốc có dấu
export type SearchSuggestion =
  | { type: 'PRODUCT'; label: string; id: number; code: null; thumbnailUrl: string | null; price: number }
  | { type: 'CATEGORY'; label: string; id: number; code: null; thumbnailUrl: null; price: null }
  | { type: 'SKILL'; label: string; id: null; code: string; thumbnailUrl: null; price: null }

export interface ProductImage {
  id: number
  url: string
  displayOrder: number
  thumbnail: boolean
}

export interface ProductDetail extends Omit<ProductSummary, 'thumbnailUrl'> {
  description: string | null
  videoUrl: string | null
  // thông số vận chuyển của gói hàng sau đóng gói
  weightGrams: number
  lengthCm: number
  widthCm: number
  heightCm: number
  images: ProductImage[]
  ratingBreakdown: Record<string, number>
  soldCount: number
  createdAt: string
}

// fit chỉ dùng cho danh sách "Hợp với bé": bổ sung nhiều nhất cho hồ sơ kỹ năng của bé
export type ProductSort = 'relevance' | 'price_asc' | 'price_desc' | 'newest' | 'name' | 'fit'

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
  // địa chỉ đầy đủ để hiển thị; đơn cũ là chuỗi địa chỉ tự do
  shippingAddress: string
  // null ở đơn đặt trước khi có địa chỉ 3 cấp
  address: Address | null
  // totalAmount = subtotal (tiền hàng) + shippingFee; đơn cũ: shippingFee = 0, miền / số kiện = null
  subtotal: number
  shippingFee: number
  shippingZone: ShippingRegion | null
  shippingZoneLabel: string | null
  parcelCount: number | null
  totalAmount: number
  status: OrderStatus
  paymentMethod: PaymentMethod
  // khách tự huỷ lúc đơn còn chờ xác nhận: không gửi phản hồi về đơn được nữa
  cancelledByCustomer: boolean
  // đơn MoMo chưa thanh toán: quá hạn này đơn tự huỷ (UnpaidOrderPolicy); null ở đơn khác
  paymentDeadline: string | null
  items: OrderItem[]
  createdAt: string
}

export interface OrderSummary {
  id: number
  orderCode: string
  customerName: string
  customerEmail: string
  subtotal: number
  shippingFee: number
  totalAmount: number
  status: OrderStatus
  paymentMethod: PaymentMethod
  createdAt: string
}

export interface CreateOrderRequest {
  receiverName: string
  receiverPhone: string
  provinceCode: number
  districtCode: number
  // null khi huyện không có cấp xã
  wardCode: number | null
  addressDetail: string
  paymentMethod: PaymentMethod
  // phí ship khách đã thấy; server tự tính lại, lệch thì trả 409 kèm phí mới
  expectedShippingFee?: number
  childAssignments: { productId: number; childProfileId: number }[]
}

// ===== Địa chỉ (63 tỉnh/thành trước sáp nhập 07/2025) & phí vận chuyển =====

export type ShippingRegion = 'MIEN_NAM' | 'MIEN_TRUNG' | 'MIEN_BAC'

export interface LocationOption {
  code: number
  name: string
}

export interface ProvinceOption extends LocationOption {
  region: ShippingRegion
  regionLabel: string
}

// hasWards = false: huyện đảo không có cấp xã, không cần chọn phường/xã
export interface DistrictOption extends LocationOption {
  hasWards: boolean
}

export interface Address {
  provinceCode: number
  provinceName: string
  districtCode: number
  districtName: string
  wardCode: number | null
  wardName: string | null
  addressDetail: string
  fullAddress: string
}

// để trống cả 4 field địa chỉ = xóa địa chỉ mặc định
export interface UpdateProfileRequest {
  fullName: string
  phone: string
  defaultProvinceCode: number | null
  defaultDistrictCode: number | null
  defaultWardCode: number | null
  defaultAddressDetail: string | null
}

export interface ShippingQuote {
  provinceCode: number
  provinceName: string
  zone: ShippingRegion
  zoneLabel: string
  actualWeightGrams: number
  volumetricWeightGrams: number
  chargeableWeightGrams: number
  parcelCount: number
  baseFee: number
  bulkySurcharge: number
  totalFee: number
  warnings: string[]
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

// hồ sơ dự kiến nếu bé nhận cùng lúc nhiều món; gains gồm mọi nhóm kỹ năng
export interface SkillBundlePreview {
  childProfileId: number
  alreadyOwnedProductIds: number[]
  gains: SkillGain[]
}

// một sản phẩm trong danh sách "Hợp với bé"
export interface ProductMatch {
  product: ProductSummary
  fitScore: number
  gains: SkillGain[]
}

// ===== Thông báo =====

export type NotificationType =
  | 'ORDER_STATUS'
  | 'SKILL_PROFILE_UPDATED'
  | 'COMPLAINT_UPDATED'
  | 'NEW_ORDER'
  | 'ORDER_CANCELLED_BY_CUSTOMER'
  | 'NEW_COMPLAINT'
  | 'NEW_REVIEW'

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  message: string | null
  link: string | null
  read: boolean
  createdAt: string
}

export interface UnreadCount {
  total: number
  byType: Partial<Record<NotificationType, number>>
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
  // yêu cầu trả hàng: lúc được duyệt, hạn chót gửi hàng về (7 ngày sau khi duyệt),
  // và có bị hệ thống tự từ chối vì khách trễ hạn không
  acceptedAt: string | null
  returnDeadline: string | null
  returnExpired: boolean
  // video mở hàng, ảnh / video tình trạng sản phẩm khách đính kèm
  attachments: ComplaintAttachment[]
  // admin giữ lại bằng chứng (không tự xóa); ngày file sẽ tự xóa, null nếu chưa đóng / đang giữ / đã xóa hết
  evidenceHold: boolean
  evidencePurgeAt: string | null
}

export interface ComplaintAttachment {
  id: number
  kind: 'UNBOXING_VIDEO' | 'CONDITION'
  contentType: string
  originalName: string
  sizeBytes: number
  // lúc file bị xóa tự động (hết hạn lưu); null = vẫn xem được
  purgedAt: string | null
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
  weightGrams: number
  lengthCm: number
  widthCm: number
  heightCm: number
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
  // tiền hàng các đơn đã giao, không gồm phí ship
  deliveredRevenue: number
  deliveredShippingFees: number
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

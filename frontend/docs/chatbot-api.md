# Hợp đồng API: Chatbot AI và cơ sở tri thức (RAG)

Giao diện chatbot đã làm xong và đang chạy trên **bản giả lập** (`src/api/mock/chatMock.ts`).
Khi backend có đủ các endpoint dưới đây:

1. Tạo `frontend/.env` (hoặc `.env.local`) với dòng `VITE_CHAT_MOCK=false`.
2. Khởi động lại `npm run dev`.

Không phải sửa component nào, vì mọi lời gọi đều đi qua `src/api/chat.ts`. Kiểu dữ liệu TypeScript nằm trong
`src/api/types.ts`, mục "Chatbot AI & cơ sở tri thức".

Quy ước chung giống các API khác của dự án:

- Mọi response bọc trong `ApiResponse { success, message, data }`. Bảng dưới chỉ mô tả phần `data`.
- Mọi endpoint đều yêu cầu JWT.
  - `/chat/**` chỉ dành cho **CUSTOMER** và chỉ truy cập được hội thoại của chính mình. Hội thoại của người khác thì trả 404.
  - `/admin/**` chỉ dành cho **ADMIN**.
- Thời gian dạng `LocalDateTime` không kèm múi giờ, ví dụ `2026-09-30T10:15:00`.
- Trang (`Page<T>`) có dạng `{ content, page, size, totalElements, totalPages }`, với `page` bắt đầu từ 0.
- API key của dịch vụ AI chỉ nằm ở server (biến môi trường), không trả về client dưới bất kỳ hình thức nào.

## 1. Khách hàng: `/api/chat`

| Method | Path | Body / query | `data` trả về |
|---|---|---|---|
| GET | `/chat/sessions` | | `ChatSessionSummary[]`, mới nhất trước |
| POST | `/chat/sessions` | `{ childProfileId: number \| null }` | `ChatSessionDetail` (chưa có `messages`) |
| GET | `/chat/sessions/{id}` | | `ChatSessionDetail` |
| PATCH | `/chat/sessions/{id}` | `{ childProfileId: number \| null }` | `ChatSessionSummary` |
| DELETE | `/chat/sessions/{id}` | | `null` |
| POST | `/chat/sessions/{id}/messages` | `{ content: string }` (1–1000 ký tự) | `ChatReply` |
| GET | `/chat/suggestions` | `?childProfileId=` (tuỳ chọn) | `string[]`, 3–4 câu hỏi gợi ý |

`childProfileId` phải là hồ sơ bé của chính khách hàng đó, nếu không thì trả 400 hoặc 404.

```ts
ChatSessionSummary = {
  id, title /* câu hỏi đầu tiên, null nếu chưa hỏi */, startedAt, lastMessageAt,
  messageCount, childProfileId, childName
}
ChatSessionDetail = ChatSessionSummary & { messages: ChatMessage[] } // sắp theo createdAt tăng dần

ChatMessage = {
  id, sender: 'USER' | 'BOT', content /* markdown tối giản: **đậm**, *nghiêng*, dòng "- " */,
  topic /* chủ đề để thống kê, ví dụ "Gợi ý theo kỹ năng" */, createdAt,
  responseTimeMs /* 0 với tin USER */,
  suggestedProducts: ChatSuggestedProduct[], // chỉ tin BOT có
  sources: ChatSource[]                      // đoạn tri thức RAG đã dùng làm căn cứ
}
ChatSuggestedProduct = {
  productId, name, price, minAge, maxAge, thumbnailUrl, skillImpacts: SkillImpact[],
  reason /* lý do gợi ý, hiển thị "Vì sao: ..." */
}
ChatSource = { documentId, documentTitle, snippet }
ChatReply  = { userMessage: ChatMessage, botMessage: ChatMessage }
```

Luồng xử lý `POST /chat/sessions/{id}/messages`, theo mục C của đề:

1. Lưu tin USER.
2. Phân tích yêu cầu: độ tuổi, nhóm kỹ năng, sản phẩm được nhắc tới.
3. Nếu phiên có hồ sơ bé thì lấy thêm tuổi, `SkillProfile` và đồ chơi bé đã có.
4. Truy xuất `topK` đoạn `DocumentChunk` từ các tài liệu `INDEXED`.
5. Gọi mô hình AI theo `ChatbotConfig` đang active.
6. Lưu tin BOT kèm `topic`, `responseTimeMs` và `suggestedProducts`. `productId` phải tồn tại và đang bán.
7. Nếu phiên chưa có `title` thì lấy câu hỏi đầu tiên làm `title`.

Nên đặt timeout phía server dưới 60 giây, vì frontend chờ tối đa 60 giây.

`reason` và `sources` hiện chưa có cột trong entity. Có thể tính lúc trả lời rồi chỉ trả trong response. Nếu muốn
xem lại được trong lịch sử thì thêm cột: `reason` vào bảng nối `chat_message_products`, và `sources` dạng JSON vào
`chat_messages`.

## 2. Quản trị: giám sát chatbot

| Method | Path | Body / query | `data` trả về |
|---|---|---|---|
| GET | `/admin/chat/stats` | | `ChatStats` |
| GET | `/admin/chat/sessions` | `?keyword=&page=&size=` | `Page<AdminChatSessionSummary>` |
| GET | `/admin/chat/sessions/{id}` | | `AdminChatSessionDetail` |

`keyword` tìm theo tên hoặc email khách hàng, tên bé, tiêu đề, và cả nội dung tin nhắn.

```ts
AdminChatSessionSummary = ChatSessionSummary & { customerId, customerName, customerEmail }
AdminChatSessionDetail  = AdminChatSessionSummary & { messages: ChatMessage[] }
ChatStats = {
  totalSessions, totalQuestions /* số tin USER */, questionsLast7Days, avgResponseTimeMs,
  topTopics: { topic, total }[],             // nhóm theo ChatMessage.topic
  topQuestions: { question, total }[],       // câu hỏi giống nhau (đã trim, không phân biệt hoa thường)
  topSuggestedProducts: { productId, productName, total }[],
  dailyQuestions: { date /* yyyy-MM-dd */, total }[] // 14 ngày gần nhất, đủ cả ngày có total = 0
}
```

## 3. Quản trị: cơ sở tri thức

| Method | Path | Body / query | `data` trả về |
|---|---|---|---|
| GET | `/admin/knowledge-documents` | `?keyword=&status=&page=&size=` | `Page<KnowledgeDocument>` |
| POST | `/admin/knowledge-documents` | `multipart/form-data`: `title`, `file` | `KnowledgeDocument` (status `PROCESSING` hoặc `UPLOADED`) |
| PUT | `/admin/knowledge-documents/{id}` | `{ title }` | `KnowledgeDocument` |
| PATCH | `/admin/knowledge-documents/{id}/status` | `{ status: 'INDEXED' \| 'DISABLED' }` | `KnowledgeDocument` |
| POST | `/admin/knowledge-documents/{id}/reindex` | | `KnowledgeDocument` (status `PROCESSING`) |
| DELETE | `/admin/knowledge-documents/{id}` | | `null`; xoá luôn các chunk |
| GET | `/admin/knowledge-documents/{id}/chunks` | | `DocumentChunk[]` theo `chunkIndex` |

```ts
KnowledgeDocument = {
  id, title, fileUrl, fileType /* PDF | DOCX | TXT | MD */,
  status: 'UPLOADED' | 'PROCESSING' | 'INDEXED' | 'DISABLED',
  uploadedAt, uploadedByName, chunkCount
}
DocumentChunk = { id, chunkIndex, content, embedded /* embedding != null */ }
```

- Chỉ nhận tệp `pdf`, `docx`, `txt`, `md`, tối đa 10 MB. Frontend có kiểm tra trước, nhưng backend vẫn phải kiểm tra lại.
- Nên tách đoạn và tạo embedding **bất đồng bộ**. Khi còn tài liệu `PROCESSING`, frontend hỏi lại danh sách mỗi 2 giây.
- Tài liệu đang `PROCESSING` thì không được đổi status: trả 409 với message `Document is still being processed`.
- Chatbot chỉ truy xuất chunk của tài liệu `INDEXED`.

## 4. Quản trị: cấu hình chatbot

| Method | Path | Body | `data` trả về |
|---|---|---|---|
| GET | `/admin/chatbot-configs` | | `ChatbotConfig[]` |
| POST | `/admin/chatbot-configs` | `ChatbotConfigRequest` | `ChatbotConfig` (`active=false`) |
| PUT | `/admin/chatbot-configs/{id}` | `ChatbotConfigRequest` | `ChatbotConfig` |
| PATCH | `/admin/chatbot-configs/{id}/activate` | | `ChatbotConfig`; tắt mọi cấu hình khác |

```ts
ChatbotConfig = { id, modelName, temperature, maxTokens, topK, systemPrompt, active, updatedAt, adminName }
ChatbotConfigRequest = {
  modelName /* 1–100 ký tự */, temperature /* 0–1 */, maxTokens /* 64–8192 */,
  topK /* 1–20 */, systemPrompt /* ≤ 4000 ký tự, có thể null */
}
```

## 5. Thống kê ở trang Tổng quan admin

`GET /admin/stats` (đã có) đang trả `chatQuestions` và `chatTopics`. Nên tính hai giá trị này từ cùng nguồn dữ liệu với
`/admin/chat/stats`, để hai trang hiển thị cùng số liệu.

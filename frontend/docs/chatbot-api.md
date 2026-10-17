# Hợp đồng API: Chatbot AI và cơ sở tri thức (RAG)

Backend đã có đủ các endpoint dưới đây (`ChatController`, `AdminChatController`, `AdminKnowledgeController`,
`AdminChatbotConfigController`). Frontend gọi qua `src/api/chat.ts`; kiểu dữ liệu TypeScript nằm trong
`src/api/types.ts`, mục "Chatbot AI & cơ sở tri thức".

Để chatbot trả lời được, backend cần biến môi trường `GOOGLE_API_KEY` (Gemini) trong `backend/.env`. Thiếu key thì
app vẫn chạy, chỉ API gửi câu hỏi và tạo embedding trả 503. Lần chạy đầu backend tự tạo một cấu hình chatbot active
(model `gemini-flash-latest`), admin đổi được ở trang Chatbot.

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

Luồng xử lý `POST /chat/sessions/{id}/messages`, theo mục C của đề (`ChatService.sendMessage`):

1. Phân tích yêu cầu: độ tuổi (từ hồ sơ bé hoặc dạng "7 tuổi" trong câu hỏi) và từ khóa để tìm sản phẩm liên quan.
2. Nếu phiên có hồ sơ bé thì lấy thêm tuổi, `SkillProfile`, đồ chơi bé đã có và gợi ý `recommendNext`.
3. Truy xuất tối đa `topK` đoạn `DocumentChunk` của tài liệu `INDEXED` có độ tương đồng cosine ≥
   `app.knowledge.min-score` (mặc định 0.6). Lỗi tạo embedding câu hỏi thì vẫn trả lời, chỉ không có `sources`.
4. Gọi Gemini theo `ChatbotConfig` đang active. Ngữ cảnh gồm mô tả, danh mục và chỉ số kỹ năng của sản phẩm.
5. Lưu cùng lúc tin USER và tin BOT (kèm `topic`, `responseTimeMs`, `suggestedProducts`). AI lỗi thì không lưu gì.
   `productId` phải nằm trong danh sách ứng viên đã gửi cho AI, tồn tại và đang bán.
6. Nếu phiên chưa có `title` thì lấy câu hỏi đầu tiên làm `title`.

`topic` là một trong các giá trị cố định (để thống kê gom nhóm được): `Chào hỏi`, `Lợi ích sản phẩm`,
`Độ tuổi phù hợp`, `Gợi ý theo kỹ năng`, `Gợi ý sản phẩm tiếp theo`, `Phân tích hồ sơ kỹ năng`,
`Kiến thức giáo dục STEM`, `Khác`. Tin USER có `topic = null`.

`reason` và `sources` được lưu trong `ChatMessage.responseMetadataJson`, nên xem lại lịch sử vẫn thấy.
`snippet` là đoạn trích tối đa 300 ký tự của đoạn tri thức.

Mã lỗi riêng của API này:

- 503: AI chưa sẵn sàng (thiếu `GOOGLE_API_KEY`, chưa có cấu hình active, Gemini lỗi; model quá tải 429/503 được thử lại 1 lần, mỗi lần tối đa 25 giây).
- 409: hội thoại bị đổi (đổi bé, gửi câu khác) trong lúc chờ AI; gửi lại câu hỏi.
- 429: khách gửi quá `app.chat.max-questions-per-minute` câu hỏi (mặc định 10) trong 1 phút.

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
| POST | `/admin/knowledge-documents/{id}/file` | `multipart/form-data`: `file` | `KnowledgeDocument` (status `PROCESSING`, `chunkCount` 0); xoá chunk cũ, lập chỉ mục lại từ file mới; đang `PROCESSING` trả 409 |
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

- Chỉ nhận tệp `pdf`, `docx`, `txt`, `md`, tối đa 10 MB. Frontend kiểm tra trước, backend kiểm tra lại (cả nội dung tệp).
- Tách đoạn và tạo embedding chạy **bất đồng bộ** (`KnowledgeIndexingWorker`). Khi còn tài liệu `PROCESSING`, frontend hỏi lại danh sách mỗi 2 giây.
- Đọc tệp hoặc tạo embedding lỗi thì tài liệu về `UPLOADED` (giao diện hiện "Lỗi xử lý"); admin bấm lập chỉ mục lại.
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
  modelName /* 1–100 ký tự */, temperature /* 0–1 */, maxTokens /* 1024–8192, token suy luận của model cũng tính vào đây */,
  topK /* 1–20 */, systemPrompt /* ≤ 4000 ký tự, có thể null */
}
```

## 5. Thống kê ở trang Tổng quan admin

`GET /admin/stats` trả `chatQuestions` và `chatTopics`, tính bằng cùng truy vấn của `ChatMessageRepository`
(`countBySender(USER)`, `countByTopic`) với `/admin/chat/stats`, nên hai trang hiển thị cùng số liệu.

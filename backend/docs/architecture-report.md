# Khảo sát kiến trúc hệ thống BrainBlocks (phục vụ mục 2.2.1 Thiết kế kiến trúc)

Khảo sát ngày 12/10/2026 trên nhánh `master` (commit `d696a45`). Mọi đường dẫn dưới đây tính từ gốc repo `D:\Nien_Luan`.
Viết tắt: `BE` = `backend/src/main/java/com/brainblocks/backend`, `FE` = `frontend/src`.

> Lưu ý về frontend: thư mục `D:\nienluan-frontend` chỉ là khung Vite ban đầu (7 file, `src/pages` và `src/routes` rỗng,
> không có router). Frontend thật nằm trong repo ở `D:\Nien_Luan\frontend` (109 file, đã commit). Báo cáo dựa trên bản này.

## 1. Bảng khảo sát thành phần

### 1.1. Client (Frontend)

| Thành phần | Công nghệ | Trạng thái | Bằng chứng |
|---|---|---|---|
| Ứng dụng SPA | React 19, TypeScript 6, Vite 8, Tailwind CSS 4 | [ĐÃ CÀI ĐẶT] | `frontend/package.json`, `frontend/vite.config.ts` |
| Định tuyến | React Router 7, lazy-load từng trang; layout `ShopLayout` (khách) và `/admin` (`AdminLayout`) | [ĐÃ CÀI ĐẶT] | `FE/App.tsx` |
| Quản lý trạng thái | TanStack Query (dữ liệu server) + React Context: `AuthProvider`, `ChatProvider`, `ToastProvider` | [ĐÃ CÀI ĐẶT] | `FE/main.tsx`, `FE/auth/AuthContext.tsx`, `FE/features/chat/ChatProvider` |
| Gọi API | Axios, `baseURL = VITE_API_URL ?? '/api'`; interceptor gắn `Authorization: Bearer <JWT>`, gặp 401 thì xóa phiên | [ĐÃ CÀI ĐẶT] | `FE/api/client.ts`, `FE/api/endpoints.ts` |
| Lưu JWT phía client | Bộ nhớ + `localStorage` | [ĐÃ CÀI ĐẶT] | `FE/auth/tokenStorage.ts` |
| Chặn trang theo vai trò | `RequireAuth role="ADMIN" / "CUSTOMER"` | [ĐÃ CÀI ĐẶT] | `FE/auth/RequireAuth.tsx` |
| Proxy khi phát triển | Vite proxy `/api`, `/uploads` → `http://localhost:8080` | [ĐÃ CÀI ĐẶT] | `frontend/vite.config.ts` |
| Nhóm trang Khách hàng/Phụ huynh | Trang chủ, danh sách/chi tiết sản phẩm, giỏ hàng, checkout, đơn hàng, hồ sơ bé, hồ sơ kỹ năng, chatbot, voucher, thông báo, phản hồi/khiếu nại, tài khoản, kết quả MoMo | [ĐÃ CÀI ĐẶT] | `FE/pages/*`, `FE/pages/{cart,orders,children,chat,account,payment}` |
| Biểu đồ kỹ năng | Recharts: radar + timeline | [ĐÃ CÀI ĐẶT] | `FE/features/skills/SkillRadar.tsx`, `FE/features/skills/SkillTimelineChart.tsx` |
| Nhóm trang Admin | Dashboard thống kê, sản phẩm, danh mục & kỹ năng, đơn hàng, người dùng, đánh giá, khiếu nại, chatbot, cơ sở tri thức, voucher | [ĐÃ CÀI ĐẶT] | `FE/pages/admin/*.tsx` |
| Đăng nhập Google / bằng mã OTP | Chỉ có nút, báo "sắp có"; backend không có endpoint | [MỚI CÓ KHUNG – chỉ UI] | `FE/pages/auth/LoginPage.tsx:136-144`; `BE/controller/AuthController.java` (không có OAuth) |

### 1.2. Backend Server (Spring Boot)

| Thành phần | Công nghệ | Trạng thái | Bằng chứng |
|---|---|---|---|
| Nền tảng | Spring Boot 4.1.1, Java 21, Spring Web MVC, Validation, Lombok | [ĐÃ CÀI ĐẶT] | `backend/pom.xml` |
| Định dạng phản hồi | `ApiResponse { success, message, data }`, `PageResponse`; lỗi tập trung ở `GlobalExceptionHandler` | [ĐÃ CÀI ĐẶT] | `BE/dto/response/ApiResponse.java`, `BE/exception/GlobalExceptionHandler.java` |
| Bảo mật, xác thực | Spring Security, stateless; `JwtAuthenticationFilter` (OncePerRequestFilter, đọc header Bearer) đặt trước `UsernamePasswordAuthenticationFilter`; JJWT 0.12.6; mật khẩu BCrypt | [ĐÃ CÀI ĐẶT] | `BE/config/SecurityConfig.java`, `BE/security/JwtAuthenticationFilter.java`, `BE/security/JwtService.java`, `BE/config/ApplicationConfig.java` |
| Phân quyền | Vai trò `ADMIN`, `CUSTOMER`; `/api/admin/**` → `hasRole("ADMIN")`; `@PreAuthorize` ở controller; GET sản phẩm/danh mục/kỹ năng/địa giới công khai | [ĐÃ CÀI ĐẶT] | `BE/enums/Role.java`, `BE/config/SecurityConfig.java` |
| CORS | Cho phép `CORS_ORIGINS` (mặc định `http://localhost:5173`) trên `/api/**` | [ĐÃ CÀI ĐẶT] | `BE/config/SecurityConfig.java`, `backend/src/main/resources/application.properties` |
| Khối **Xác thực & Tài khoản** | `AuthController` (`/api/auth`: login, register, send-code, forgot/reset password), `UserController` (`/api/me`), `AdminUserController` (`/api/admin/users`); `AuthService`, `EmailVerificationService`, `PasswordResetService`, `UserService`, `AdminUserService` | [ĐÃ CÀI ĐẶT] | `BE/controller/AuthController.java`, `BE/service/auth/*`, `BE/service/user/*` |
| Khối **Sản phẩm, Danh mục & Kỹ năng** | `ProductController`, `CatalogController`, `AdminProductController`, `AdminProductImageController`, `AdminCatalogController`; `ProductService` (lọc/sắp xếp bằng Specification), `SearchSuggestionService`, `ProductImageService`, `CategoryService`, `SkillService`; entity `Product`, `ProductSkillImpact`, `Skill`, `Category`, `ProductImage` | [ĐÃ CÀI ĐẶT] | `BE/controller/product/*`, `BE/controller/admin/AdminProduct*.java`, `BE/service/product/*`, `BE/service/skill/SkillService.java` |
| Khối **Hồ sơ bé & Hồ sơ kỹ năng** | `ChildProfileController` (`/api/children`), `SkillProfileController` (profile, recalculate, timeline, preview, recommendations), `ProductMatchController`; `ChildProfileService`, `SkillProfileService`, `SkillScoreCalculator` (hệ số alpha), `SkillRecommendationService.recommendNext`, `ProductMatchService`; entity `ChildProfile`, `ChildProduct` (nguồn `PURCHASED`/`MANUAL`), `SkillProfile`, `SkillScore` | [ĐÃ CÀI ĐẶT] | `BE/controller/SkillProfileController.java`, `BE/service/skill/*`, `BE/service/child/*`, `BE/enums/ProductSource.java` |
| Khối **Giỏ hàng – Đơn hàng – Vận chuyển – Voucher** | `CartController`, `OrderController`, `AdminOrderController`, `ShippingController`, `LocationController`, `VoucherController`, `AdminVoucherController`; trạng thái đơn `PENDING → CONFIRMED → SHIPPING → DELIVERED / CANCELLED` | [ĐÃ CÀI ĐẶT] | `BE/controller/{cart,order,shipping,location,voucher}/*`, `BE/enums/OrderStatus.java` |
| Tính phí vận chuyển | Tự tính nội bộ (theo miền, khối lượng quy đổi, phụ phí cồng kềnh), **không gọi API hãng vận chuyển** | [ĐÃ CÀI ĐẶT] | `BE/service/shipping/ShippingCalculator.java`, `application.properties` (`app.shipping.*`) |
| Dữ liệu địa giới hành chính | File JSON tĩnh nạp vào bộ nhớ, **không gọi API ngoài** | [ĐÃ CÀI ĐẶT] | `backend/src/main/resources/locations/vn-locations.json`, `BE/service/location/LocationDirectory.java` |
| Khối **Thanh toán** | `PaymentController` (`/api/payments`: `/{orderId}/create-url`, `/momo-return`, `/momo-ipn`, `/{orderId}`); `PaymentService`; interface `PaymentGateway` với một cài đặt `MoMoGateway` (ký HMAC-SHA256); phương thức `COD`, `MOMO` | [ĐÃ CÀI ĐẶT] | `BE/controller/payment/PaymentController.java`, `BE/service/payment/*`, `BE/enums/PaymentMethod.java` |
| Khối **Đánh giá – Khiếu nại – Thông báo** | `ReviewService`, `CustomerFeedbackController`, `AdminFeedbackController`; `ComplaintController`, `AdminComplaintController` (đổi/trả/hủy, tệp bằng chứng); `NotificationController` (thông báo trong ứng dụng, lấy bằng REST, không WebSocket) | [ĐÃ CÀI ĐẶT] | `BE/controller/review/*`, `BE/controller/complaint/*`, `BE/controller/admin/AdminComplaintController.java`, `BE/service/notification/NotificationService.java` |
| Khối **Chatbot AI & RAG** | `ChatController` (`/api/chat`: sessions, messages, suggestions), `AdminChatController` (`/api/admin/chat`: lịch sử, thống kê), `AdminKnowledgeController` (`/api/admin/knowledge-documents`), `AdminChatbotConfigController`; `ChatService`, `GeminiService`, `KnowledgeBaseService`, `KnowledgeIndexingWorker`, `KnowledgeIndexingRecovery`, `ChatbotConfigService`, `ChatRateLimiter` (10 câu/phút) | [ĐÃ CÀI ĐẶT] | `BE/controller/chat/ChatController.java`, `BE/controller/admin/Admin{Chat,Knowledge,ChatbotConfig}Controller.java`, `BE/service/ai/*`, `backend/docs/chatbot-services.md` |
| Khối **Thống kê quản trị** | `AdminStatsService` (khách hàng, sản phẩm, tồn kho thấp, đơn theo ngày, top bán/xem, kỹ năng được quan tâm, số câu hỏi chatbot, chủ đề tư vấn) qua `GET /api/admin/stats` | [ĐÃ CÀI ĐẶT] | `BE/service/stats/AdminStatsService.java`, `BE/dto/response/stats/AdminStatsResponse.java`, `BE/controller/admin/AdminFeedbackController.java:43` |
| Tầng truy cập dữ liệu | Spring Data JPA / Hibernate, 27 repository, `ddl-auto=update` | [ĐÃ CÀI ĐẶT] | `BE/repository/*`, `application.properties` |
| Tác vụ nền theo lịch | `@Scheduled`: tự hủy đơn MoMo quá hạn thanh toán, tự từ chối đổi/trả quá hạn gửi hàng, tự xóa tệp bằng chứng hết hạn lưu | [ĐÃ CÀI ĐẶT] | `BE/config/UnpaidOrderExpiryJob.java`, `BE/config/ReturnExpiryJob.java`, `BE/config/EvidencePurgeJob.java` |
| Xử lý bất đồng bộ | `@Async` + `ThreadPoolTaskExecutor` `knowledgeExecutor` cho lập chỉ mục tài liệu; gửi mail bất đồng bộ | [ĐÃ CÀI ĐẶT] | `BE/config/KnowledgeAsyncConfig.java`, `BE/config/MailConfig.java` |
| Dữ liệu khởi tạo | Seeder admin, danh mục mẫu, cấu hình chatbot mặc định | [ĐÃ CÀI ĐẶT] | `BE/config/AdminSeeder.java`, `CatalogSeeder.java`, `ChatbotConfigSeeder.java` |

### 1.3. Lưu trữ dữ liệu

| Thành phần | Công nghệ | Trạng thái | Bằng chứng |
|---|---|---|---|
| CSDL quan hệ | PostgreSQL (driver `postgresql`; `DB_URL=jdbc:postgresql://localhost:5432/brainblocks`). H2 chỉ dùng cho test | [ĐÃ CÀI ĐẶT] | `backend/pom.xml`, `backend/.env.example`, `application.properties` |
| Lưu vector embedding | Cột `embedding` kiểu `float[]` trong bảng chunk của PostgreSQL; độ tương đồng cosine tính bằng Java, lọc ngưỡng `0.6`, lấy topK | [ĐÃ CÀI ĐẶT – không phải vector DB] | `BE/entity/DocumentChunk.java:35-36`, `backend/docs/chatbot-services.md` mục 13–14 |
| pgvector / vector store chuyên dụng | Comment ghi "sẽ đổi sang kiểu vector khi dùng pgvector", chưa có dependency/extension nào | [CHƯA CÓ] | `BE/entity/DocumentChunk.java:35`; không tìm thấy `pgvector` trong `pom.xml` |
| Redis / cache phân tán | Không tìm thấy (chỉ có HTTP `Cache-Control` cho dữ liệu địa giới) | [CHƯA CÓ – đề cũng không yêu cầu] | `backend/pom.xml`; `BE/controller/location/LocationController.java:25` |
| Lưu tệp trên máy chủ | Hệ thống tệp cục bộ: `uploads/` (ảnh sản phẩm, phục vụ công khai qua `/uploads/**`), `knowledge-files/` (tài liệu tri thức, riêng tư), `private-uploads/complaints` (bằng chứng khiếu nại, chỉ tải qua API có kiểm tra quyền). Không dùng S3/Cloudinary | [ĐÃ CÀI ĐẶT] | `application.properties` (`app.upload.dir`, `app.knowledge.dir`, `app.complaint.evidence-dir`), `BE/config/WebConfig.java`, `BE/service/ai/KnowledgeDocumentStorage.java`, `BE/service/complaint/ComplaintEvidenceStorage.java` |

### 1.4. Dịch vụ bên ngoài

| Thành phần | Công nghệ | Trạng thái | Bằng chứng |
|---|---|---|---|
| LLM sinh câu trả lời | Google Gemini qua SDK `com.google.genai:google-genai` 1.75.0; model mặc định `gemini-flash-latest` (admin đổi được); trả JSON theo schema (content, topic, products); API key `GOOGLE_API_KEY` chỉ ở server | [ĐÃ CÀI ĐẶT] | `backend/pom.xml`, `BE/config/GeminiConfig.java`, `BE/service/ai/GeminiService.java:59-63`, `application.properties` |
| Embedding | Gemini `gemini-embedding-001`, 768 chiều; `RETRIEVAL_DOCUMENT` (lô 50) khi lập chỉ mục, `RETRIEVAL_QUERY` khi hỏi | [ĐÃ CÀI ĐẶT] | `BE/service/ai/GeminiService.java:18-19, 88-107` |
| Cổng thanh toán | MoMo (môi trường test `test-payment.momo.vn`), redirect về `/payment/momo-return`, IPN về `/api/payments/momo-ipn` | [ĐÃ CÀI ĐẶT] | `BE/service/payment/MoMoGateway.java`, `application.properties` (`momo.*`) |
| VNPay | Không tìm thấy | [CHƯA CÓ – đề không yêu cầu] | — |
| Email | Gmail SMTP (`spring-boot-starter-mail`, `JavaMailSender`): mã xác minh đăng ký, link đặt lại mật khẩu | [ĐÃ CÀI ĐẶT] | `BE/service/mail/MailService.java`, `BE/config/MailConfig.java`, `application.properties` (`app.mail.*`) |
| Đọc tài liệu tri thức | Thư viện nội bộ (không phải dịch vụ ngoài): Apache PDFBox 3.0.8 (PDF), Apache POI 5.5.1 (DOCX), UTF-8 (TXT/MD) | [ĐÃ CÀI ĐẶT] | `backend/pom.xml`, `BE/service/ai/KnowledgeIndexingWorker.java` |
| OAuth Google | Không có ở backend | [CHƯA CÓ – đề không yêu cầu] | `BE/controller/AuthController.java` |

## 2. Luồng phối hợp giữa các thành phần

- **Client ↔ Server:** React SPA gọi REST API dưới tiền tố `/api` bằng Axios; mỗi request của người đã đăng nhập mang header `Authorization: Bearer <JWT>`. Ở server, `JwtAuthenticationFilter` xác thực token, Spring Security phân quyền theo vai trò (`/api/admin/**` chỉ dành cho ADMIN), controller gọi service nghiệp vụ và trả JSON thống nhất dạng `{ success, message, data }`. Phiên làm việc không trạng thái (stateless).
- **Server ↔ PostgreSQL và tệp:** Service truy cập dữ liệu qua repository Spring Data JPA/Hibernate. Ảnh sản phẩm, tài liệu tri thức và bằng chứng khiếu nại lưu trên hệ thống tệp của máy chủ; CSDL chỉ giữ thông tin mô tả và đường dẫn.
- **Hồ sơ kỹ năng:** khi đơn được giao (hoặc phụ huynh tự gán sản phẩm cho bé), sản phẩm vào hồ sơ bé; `SkillScoreCalculator` cộng dồn chỉ số tác động (Logic, Sáng tạo, Giải quyết vấn đề, STEM) thành điểm kỹ năng, frontend vẽ biểu đồ radar/timeline, và `SkillRecommendationService` gợi ý sản phẩm bù vào nhóm kỹ năng còn yếu.
- **RAG – lập chỉ mục:** Admin tải tài liệu (PDF/DOCX/TXT/MD) → lưu tệp, tạo bản ghi `PROCESSING` → worker bất đồng bộ trích văn bản, tách đoạn 1200 ký tự (chồng lấn 200) → gọi Gemini Embedding theo lô → lưu vector vào PostgreSQL → tài liệu chuyển `INDEXED` để chatbot dùng.
- **RAG – trả lời:** câu hỏi của phụ huynh → `ChatService` gom ngữ cảnh từ CSDL (hồ sơ bé, đồ chơi đã có, sản phẩm hợp tuổi còn hàng, lịch sử hội thoại) → nhúng câu hỏi thành vector, so cosine với các đoạn tri thức, lấy topK → gửi ngữ cảnh tới Gemini theo cấu hình chatbot đang bật → nhận JSON (nội dung, chủ đề, sản phẩm gợi ý kèm lý do) → kiểm tra lại sản phẩm, lưu tin nhắn và nguồn trích dẫn, trả về client.
- **Thanh toán MoMo và dịch vụ phụ trợ:** server tạo yêu cầu thanh toán có chữ ký HMAC-SHA256 gửi MoMo và nhận `payUrl`; trình duyệt chuyển sang trang MoMo, thanh toán xong được chuyển về frontend; MoMo đồng thời gọi IPN về server để cập nhật trạng thái thanh toán. Server gửi email qua Gmail SMTP (mã xác minh, đặt lại mật khẩu) và chạy tác vụ định kỳ (hủy đơn MoMo quá hạn, từ chối đổi/trả quá hạn, xóa bằng chứng hết hạn lưu).

## 3. Điểm chưa rõ / cần nhóm xác nhận

1. **Vector store:** code đang lưu embedding dạng mảng `float[]` trong PostgreSQL và tính cosine bằng Java, chưa dùng pgvector. Hình nên ghi đúng thực tế ("Bảng đoạn tri thức + vector embedding trong PostgreSQL") hay vẽ theo thiết kế dự kiến (pgvector)? Prompt bên dưới đang ghi theo thực tế, kèm phương án thay thế.
2. **Đường dẫn frontend:** nhóm có còn dùng `D:\nienluan-frontend` không? Bản đó chỉ là khung rỗng; bản thật ở `frontend/` trong repo.
3. **Đăng nhập Google / OTP:** chỉ là nút giao diện, đề không yêu cầu. Đề xuất KHÔNG vẽ vào hình.
4. **Khối lưu trữ tệp:** có muốn vẽ "Lưu trữ tệp trên máy chủ" thành khối riêng cạnh PostgreSQL không? Prompt đang vẽ (vì ảnh sản phẩm và tài liệu RAG thực sự nằm ở đó).
5. **Tác vụ định kỳ (scheduler) và voucher, phí vận chuyển, địa giới:** đây là phần mở rộng ngoài đề. Prompt đang gộp vào các khối có sẵn để hình không quá tải; nếu muốn tinh gọn hơn có thể bỏ.
6. **Tên model LLM:** `gemini-flash-latest` là giá trị mặc định, admin đổi được ở trang Chatbot. Hình nên ghi chung "Google Gemini API" thay vì tên model cụ thể.
7. **MoMo đang ở môi trường thử nghiệm (sandbox):** có cần ghi chú trong hình hay chỉ nhắc trong văn bản báo cáo?
8. **Vị trí file báo cáo:** file này đặt ở `backend/docs/architecture-report.md` (thư mục `docs/` của backend). Nếu muốn đặt ở gốc repo thì chuyển lại.

### Thành phần [CHƯA CÓ] / [MỚI CÓ KHUNG] — để nhóm quyết định có vẽ không

| Thành phần | Trạng thái | Có trong đề? | Đề xuất |
|---|---|---|---|
| pgvector / vector store chuyên dụng | [CHƯA CÓ] | Đề chỉ nói "cơ sở tri thức RAG", không bắt buộc pgvector | Không vẽ khối riêng; vẽ "vector embedding lưu trong PostgreSQL" |
| Đăng nhập Google / OTP | [MỚI CÓ KHUNG – chỉ UI] | Không | Không vẽ |
| VNPay, Redis, OAuth | [CHƯA CÓ] | Không | Không vẽ |

Mọi chức năng nêu trong đề bài (mục 2.1–2.14 và C) đều đã tìm thấy phần cài đặt tương ứng trong backend và frontend, nên hình không cần thành phần "thiết kế nhưng chưa làm".

## 4. PROMPT CHO CLAUDE DESIGN

```text
Hãy vẽ một SƠ ĐỒ KIẾN TRÚC HỆ THỐNG để chèn vào báo cáo niên luận (tài liệu học thuật, tiếng Việt).

=== BỐI CẢNH ===
Hệ thống "BrainBlocks" là website bán đồ chơi giáo dục STEM có tích hợp Chatbot AI tư vấn lộ trình phát triển tư duy cho trẻ. Kiến trúc tách biệt Frontend – Backend – Database. Frontend là ứng dụng React chạy trên trình duyệt; Backend là một server Spring Boot cung cấp REST API; dữ liệu lưu trong PostgreSQL; Chatbot dùng kỹ thuật RAG với dịch vụ AI bên ngoài.

=== PHONG CÁCH ===
- Sơ đồ kiến trúc kiểu báo cáo học thuật: nền trắng, khối chữ nhật bo góc nhẹ (hoặc khối 3D đơn giản cho CSDL dạng hình trụ), viền mảnh màu xám đậm, mũi tên có nhãn.
- Màu nền khối nhạt, mỗi vùng một tông: Client xanh dương nhạt, Backend xanh lá nhạt, Lưu trữ dữ liệu vàng/cam nhạt, Dịch vụ bên ngoài tím/hồng nhạt. Chữ màu đen/xám đậm.
- Toàn bộ chữ tiếng Việt, giữ nguyên thuật ngữ kỹ thuật tiếng Anh: REST API, JWT, JSON, SPA, React, Spring Boot, Spring Security, JPA/Hibernate, PostgreSQL, Embedding, LLM, RAG, IPN, SMTP, HMAC-SHA256.
- KHÔNG dùng logo thương hiệu nào (Google, MoMo, React, Spring…), chỉ dùng hộp chữ. Có thể dùng biểu tượng trung tính đơn giản (máy tính, hình trụ CSDL, đám mây) nếu cần.
- KHÔNG ghi tiêu đề trong hình (chú thích hình sẽ đặt bên ngoài).
- Khổ ngang (tỉ lệ khoảng 16:10), sao cho khi thu nhỏ vừa chiều ngang trang A4 dọc (khoảng 16 cm) chữ vẫn đọc được: tên khối lớn tương đương 14–16pt, chữ trong khối con ≥ 10–11pt, nhãn mũi tên ≥ 9–10pt. Ưu tiên ít chữ, không viết đoạn văn trong khối.
- Bố cục gọn, thẳng hàng, mũi tên không cắt chéo qua khối.

=== BỐ CỤC VÀ NỘI DUNG CÁC KHỐI ===

[1] BÊN TRÁI — khối lớn "Client – Trình duyệt web"
Dòng phụ: "React SPA (TypeScript, Vite) · React Router · TanStack Query · Axios"
Bên trong chia 2 khối con xếp dọc:
  (a) "Giao diện Khách hàng / Phụ huynh" — liệt kê dạng gạch đầu dòng ngắn:
      • Tìm kiếm, lọc & xem sản phẩm
      • Giỏ hàng, đặt hàng, thanh toán
      • Hồ sơ bé & biểu đồ kỹ năng
      • Chatbot AI tư vấn
      • Tài khoản, đơn hàng, đánh giá, khiếu nại
  (b) "Giao diện Quản trị (Admin)" — gạch đầu dòng:
      • Thống kê & báo cáo
      • Sản phẩm, danh mục, nhóm kỹ năng
      • Đơn hàng, kho, khiếu nại, đánh giá
      • Người dùng, voucher
      • Cơ sở tri thức & cấu hình Chatbot
Một biểu tượng người dùng nhỏ bên ngoài mỗi khối con: "Phụ huynh" cạnh (a), "Quản trị viên" cạnh (b).

[2] Ở GIỮA — khối lớn nhất "Backend Server (Spring Boot)"
Bên trong, từ trên xuống:
  • Dải ngang trên cùng: "Spring Security · JWT Authentication Filter · Phân quyền CUSTOMER / ADMIN"
  • Dải ngang thứ hai: "REST Controllers  (/api/**,  /api/admin/**)"
  • Vùng chính: lưới 2 cột × 4 hàng gồm 8 khối module nghiệp vụ (mỗi khối 1 tên in đậm + 1 dòng mô tả ngắn):
      1. "Xác thực & Tài khoản" — đăng ký, đăng nhập, hồ sơ, khóa/mở tài khoản
      2. "Sản phẩm, Danh mục & Kỹ năng" — chỉ số Logic / Sáng tạo / GQVĐ / STEM
      3. "Hồ sơ bé & Lộ trình kỹ năng" — tính điểm kỹ năng, gợi ý sản phẩm tiếp theo
      4. "Giỏ hàng – Đơn hàng" — vận chuyển, voucher, trạng thái đơn
      5. "Thanh toán" — COD, cổng thanh toán trực tuyến
      6. "Đánh giá – Khiếu nại – Thông báo"
      7. "Chatbot AI & RAG" — hội thoại, truy xuất tri thức, cấu hình chatbot   (tô viền đậm hơn hoặc màu nhấn để làm nổi bật vì đây là trọng tâm đề tài)
      8. "Thống kê & Báo cáo"
  • Dải ngang dưới cùng chia 2 ô:
      - "Tầng truy cập dữ liệu: Spring Data JPA / Hibernate"
      - "Tác vụ nền: lập chỉ mục tài liệu (bất đồng bộ) · tác vụ định kỳ (hủy đơn quá hạn thanh toán…)"

[3] BÊN PHẢI — vùng "Lưu trữ dữ liệu", 2 khối xếp dọc:
  • Hình trụ CSDL "PostgreSQL" với 2 dòng nhỏ bên trong:
      - "Dữ liệu nghiệp vụ: người dùng, sản phẩm, hồ sơ bé, đơn hàng, hội thoại…"
      - "Đoạn tri thức + vector embedding (RAG)"
  • Khối thư mục "Lưu trữ tệp trên máy chủ": "Ảnh sản phẩm · Tài liệu tri thức · Tệp bằng chứng khiếu nại"

[4] PHÍA TRÊN Backend — vùng "Dịch vụ AI bên ngoài" (vẽ dạng đám mây hoặc khối có viền nét đứt), 1 khối chứa 2 ô:
  • "Google Gemini API – LLM (sinh câu trả lời)"
  • "Google Gemini API – Embedding (vector hóa văn bản)"

[5] PHÍA DƯỚI Backend — 2 khối dịch vụ bên ngoài đặt cạnh nhau:
  • "Cổng thanh toán MoMo"
  • "Dịch vụ Email (SMTP)"

=== MŨI TÊN VÀ NHÃN ===
Dùng mũi tên liền cho request/response đồng bộ; mũi tên nét đứt cho luồng chuyển hướng trình duyệt và callback.
1. Client → Backend (mũi tên trên, hướng phải): "HTTP Request (REST, JWT)"
   Backend → Client (mũi tên dưới, hướng trái): "Response (JSON)"
2. Backend → PostgreSQL: "Truy vấn / ghi dữ liệu (JPA/Hibernate)"
   PostgreSQL → Backend: "Bản ghi dữ liệu, vector tri thức"
3. Backend ↔ Lưu trữ tệp (2 chiều, 1 mũi tên): "Ghi / đọc tệp"
4. Module "Chatbot AI & RAG" → ô Embedding: "Đoạn văn bản / câu hỏi"
   ô Embedding → Backend: "Vector embedding"
5. Module "Chatbot AI & RAG" → ô LLM: "Câu hỏi + ngữ cảnh (sản phẩm, hồ sơ kỹ năng, tri thức)"
   ô LLM → Backend: "Câu trả lời (JSON) + sản phẩm gợi ý"
6. Module "Thanh toán" → Cổng MoMo: "Tạo yêu cầu thanh toán (ký HMAC-SHA256)"
   Cổng MoMo → Backend: "Đường dẫn thanh toán (payUrl)"
   Cổng MoMo → Backend (nét đứt, chiều ngược về server, nhãn nổi bật): "IPN – thông báo kết quả thanh toán"
7. Client ↔ Cổng MoMo (nét đứt, vòng phía dưới bên trái): "Chuyển hướng thanh toán / quay về trang kết quả"
8. Module "Xác thực & Tài khoản" → Dịch vụ Email: "Mã xác minh, link đặt lại mật khẩu"
Mũi tên nội bộ trong Backend (mảnh, không cần nhãn hoặc nhãn rất ngắn): Security → Controllers → các module → Tầng truy cập dữ liệu.

=== CHÚ GIẢI (góc dưới bên phải, nhỏ gọn) ===
• Mũi tên liền: request / response
• Mũi tên nét đứt: chuyển hướng trình duyệt, callback
• Khối viền nét đứt: dịch vụ bên ngoài

=== YÊU CẦU ĐẦU RA ===
Xuất hình ảnh độ phân giải cao (PNG hoặc SVG), nền trắng, không có tiêu đề, không logo, chữ tiếng Việt có dấu hiển thị đúng.
```

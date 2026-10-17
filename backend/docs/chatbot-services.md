# DTO và service cho cụm 4

Các DTO bám theo `frontend/docs/chatbot-api.md`. Controller: `ChatController` (`/api/chat`), `AdminChatController`,
`AdminKnowledgeController`, `AdminChatbotConfigController` (`/api/admin/...`).

## Cách đọc các dòng khai báo

- `package ...`: xác định thư mục logic của lớp trong project.
- Mỗi dòng `import ...`: nhập kiểu dữ liệu hoặc annotation dùng trong file. Không chạy nghiệp vụ ở dòng import.
- `public record ...(...)`: khai báo DTO bất biến; từng tham số là một field JSON, Java sinh constructor và accessor.
- `Long id`: khóa định danh có thể null; `String`: văn bản; `LocalDateTime`: thời gian; `List<T>`: danh sách DTO con.
- `@NotBlank`: không nhận chuỗi null/rỗng/toàn khoảng trắng.
- `@NotNull`: field phải được gửi; `@Size`: giới hạn độ dài; `@Positive`: số phải lớn hơn 0.
- `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax`: giới hạn số theo hợp đồng API.
- `@Service`/`@Component`: đăng ký đối tượng do Spring tạo và quản lý.
- `@RequiredArgsConstructor`: Lombok tạo constructor nhận các field `private final`; Spring truyền dependency vào constructor.
- `@Validated`: bật kiểm tra tham số khi gọi bean qua Spring proxy; `@Valid`: kiểm tra các field DTO.
- `@PreAuthorize`: kiểm tra vai trò trước khi chạy phương thức. API khách dùng CUSTOMER; quản trị dùng ADMIN.
- `@Transactional`: commit cả nghiệp vụ hoặc rollback khi có lỗi; `readOnly = true` dành cho truy vấn.
- `Propagation.NOT_SUPPORTED`: tạm ngưng transaction khi chờ AI để không giữ connection DB suốt lời gọi mạng.
- `TransactionTemplate.execute(...)`: mở transaction ngắn để đọc snapshot hoặc lưu kết quả.
- `Optional.orElseThrow(...)`: không tìm thấy thì trả lỗi rõ ràng thay vì dùng một đối tượng null.
- `stream().map(...).toList()`: chuyển danh sách entity thành danh sách DTO.
- Các dấu `{`/`}` mở và đóng phạm vi lớp, phương thức, vòng lặp hoặc điều kiện; dấu `;` kết thúc câu lệnh.

## ChatbotConfigService

1. Các field repository truy cập cấu hình và admin; `CurrentUserProvider` lấy người đang đăng nhập.
2. `getConfigs()` lấy các phiên bản và đổi sang DTO, không trả entity hoặc API key.
3. `createConfig()` tìm admin, tạo cấu hình `active=false`, áp dụng dữ liệu, lưu rồi flush để có timestamp.
4. `updateConfig()` khóa dòng chung trước khi đọc/sửa để không chạy chồng với kích hoạt.
5. `activateConfig()` khóa cùng một dòng cho mọi lần kích hoạt, bật cấu hình chọn và tắt các cấu hình khác trong một transaction.
6. `getActiveSettings()` trả snapshot thông số cho ChatService. Nếu chưa có active, báo dịch vụ AI chưa sẵn sàng.
7. `apply()` trim chuỗi và gán từng thuộc tính từ DTO; không cho client tự gán admin hoặc active.
8. `toResponse()` chỉ lấy các field cần hiển thị.

## KnowledgeBaseService và worker

1. `getDocuments()` tạo bộ lọc tên/trạng thái; escape `%`/`_` để từ khóa được hiểu như văn bản; đếm chunk theo lô.
2. `uploadDocument()` xác thực loại/size, lưu tên UUID vào thư mục riêng, tạo bản ghi PROCESSING.
3. Callback rollback dọn file mới; callback afterCommit mới đưa tài liệu vào worker.
4. `updateDocument()` đổi tiêu đề; `setDocumentStatus()` chỉ nhận INDEXED/DISABLED và chặn tài liệu đang xử lý.
5. Chỉ cho bật INDEXED khi có chunk và mọi chunk đã có embedding.
6. `reindexDocument()` đưa tài liệu về PROCESSING; `deleteDocument()` cascade xóa chunk rồi dọn file sau commit.
   `replaceFile()` kiểm tra và lưu file mới như lúc tải lên, xóa ngay chunk cũ (index file mới lỗi cũng không bật lại
   được nội dung cũ), đưa tài liệu về PROCESSING; commit thì xóa file cũ, rollback thì xóa file mới.
7. `getDocumentChunks()` trả nội dung, thứ tự và cờ embedded, không đưa vector lớn ra frontend.
8. Worker đọc PDF bằng PDFBox, DOCX bằng POI, TXT/MD bằng UTF-8. PDF scan không có text cần OCR riêng.
9. Tách đoạn tối đa 1200 ký tự, chồng lấn 200 ký tự; giới hạn 1 triệu ký tự và 1000 đoạn mỗi tài liệu.
10. Worker gọi embedding theo lô 50 đoạn, ngoài transaction (lỗi 429/5xx được thử lại tối đa 4 lần), rồi thay chunk và bật INDEXED trong một transaction ngắn.
11. Nếu đọc file hoặc AI lỗi, trạng thái trở về UPLOADED để admin thử reindex. Không thêm FAILED ngoài enum UML.
12. Sau khi server khởi động lại, worker tiếp tục tài liệu PROCESSING còn dang dở.
13. `retrieve()` tính topK ngay trong PostgreSQL bằng pgvector (`VectorSearchRepository.nearestChunks`, cài đặt ở
    `PgVectorSearchRepository`): chỉ chunk của tài liệu INDEXED, xếp theo khoảng cách cosine `embedding <=> câu hỏi`,
    bỏ đoạn có cosine dưới `app.knowledge.min-score` (mặc định 0.6, tức khoảng cách <= 0.4; quy đổi ở `VectorMath`).
    AI nhận nội dung đầy đủ; `snippet` lưu và hiển thị chỉ 300 ký tự.
14. Vector dùng cố định `gemini-embedding-001`, 768 chiều; cột `document_chunks.embedding` kiểu `vector(768)` có index
    HNSW `vector_cosine_ops`. Câu SQL viết theo khuyến nghị của pgvector để dùng được index: CTE `AS MATERIALIZED` chỉ
    `ORDER BY embedding <=> :q LIMIT :k`, ngưỡng lọc ở ngoài CTE; bật `hnsw.iterative_scan` để điều kiện lọc không làm
    thiếu kết quả. Cài đặt và migration: xem `pgvector-setup.md`.

## ProductEmbeddingService (tìm sản phẩm theo ngữ nghĩa)

1. Mỗi sản phẩm có 1 dòng trong `product_embeddings` (vector 768 chiều, `content_hash`, `updated_at`; xóa sản phẩm thì
   DB xóa theo). Văn bản embed (`ProductEmbeddingText`) gồm tên, danh mục, độ tuổi, mô tả (tối đa 2000 ký tự) và chỉ số
   tác động từng kỹ năng diễn đạt thành câu ("Tư duy Logic: 8/10, tác động mạnh."). Embed bằng `RETRIEVAL_DOCUMENT`,
   câu hỏi bằng `RETRIEVAL_QUERY`, giống tài liệu.
2. `content_hash` = SHA-256 của văn bản (kèm model + số chiều): chỉ gọi Gemini khi hash đổi. Đổi tồn kho, giá, ảnh không
   gọi lại; sản phẩm ngừng bán không embed.
3. Admin tạo / sửa sản phẩm, đổi tên danh mục hoặc kỹ năng → service phát `ProductContentChangedEvent`; listener chạy
   SAU commit trên `productEmbeddingExecutor` (1 luồng, chạy tuần tự). Lỗi Gemini hoặc hàng đợi đầy chỉ ghi log, việc lưu
   sản phẩm không bị ảnh hưởng. Trước khi ghi, service kiểm tra lại hash để không ghi đè bằng vector của nội dung cũ.
4. `ProductEmbeddingBackfillRunner`: lúc khởi động, chạy nền và embed các sản phẩm chưa có vector hoặc có vector cũ, theo lô
   50 (1 request Gemini mỗi lô), log `Product embeddings: 50/312 checked, 12 refreshed`. Tắt bằng
   `PRODUCT_EMBEDDING_BACKFILL=false`. Bị lỗi giữa chừng (hết quota) thì lần khởi động sau làm tiếp.

## ChatService

1. CRUD phiên chat luôn kiểm tra khách sở hữu; hồ sơ bé được kiểm tra qua ChildProfileAccessGuard.
2. `getMySessions()` lấy thống kê tin nhắn theo lô; trang danh sách không cần tải toàn bộ lịch sử.
3. `prepare()` lấy snapshot phiên, hồ sơ kỹ năng, đồ chơi đã có, ứng viên hợp tuổi/còn hàng và lịch sử gần đây.
4. Khi chưa chọn bé, tuổi được nhận diện từ dạng câu hỏi `6 tuổi`; nếu chưa rõ, chatbot có thể hỏi thêm.
5. Ứng viên sản phẩm (tối đa 20, loại trùng, theo thứ tự ưu tiên): tối đa 10 món khớp từ khóa câu hỏi (đã bỏ từ phổ
   biến, món khớp nhiều từ khóa xếp trước) → tối đa `app.chat.semantic-products.limit` (8) món gần nghĩa câu hỏi nhất
   (`nearestProducts`: MỘT câu SQL vừa lọc đang bán, còn hàng, hợp tuổi bé, chưa có, vừa xếp theo `<=>`; cosine >=
   `app.chat.semantic-products.min-score`, mặc định 0.65) → gợi ý `recommendNext` → các món khớp từ khóa còn lại.
   Nhờ bước ngữ nghĩa, câu "đồ chơi giúp bé kiên nhẫn" ra xếp hình / lắp ráp dù tên sản phẩm không có chữ "kiên nhẫn".
   Không có vector (sản phẩm chưa embed, embedding lỗi, lỗi truy vấn) thì bỏ bước này, giữ nguyên cách lấy cũ.
   Ngữ cảnh gửi AI có mô tả, danh mục, chỉ số kỹ năng của ứng viên và của đồ chơi bé đã có.
6. `sendMessage()` giới hạn 10 câu hỏi/phút mỗi khách (`ChatRateLimiter`, trả 429), kiểm tra quyền với phiên, embed câu hỏi
   MỘT lần (chỉ khi có đoạn tri thức hoặc vector sản phẩm; lỗi thì bỏ qua cả phần ngữ nghĩa lẫn tri thức) dùng cho cả tìm
   sản phẩm và topK tri thức, rồi gọi Gemini theo cấu hình active, không giữ transaction trong lúc chờ mạng.
7. Gemini trả JSON có content/topic/products. `topic` là enum cố định `GeminiService.TOPICS` (giá trị lạ quy về "Khác"),
   tối đa 5 sản phẩm. Service chỉ nhận ID trong tập ứng viên và kiểm tra lại active/tồn kho khi lưu.
8. `saveReply()` khóa phiên, kiểm tra version và tin nhắn cuối. Nếu ngữ cảnh đã đổi, trả 409 để khách thử lại.
9. USER và BOT lưu cùng transaction sau khi AI thành công. AI lỗi không để lại nửa cuộc hội thoại; tiêu đề lấy câu hỏi đầu tiên.
10. `responseMetadataJson` lưu lý do gợi ý và nguồn tri thức để lịch sử giữ được căn cứ của câu trả lời.
11. `getSessionsForAdmin()` tìm theo tên/email khách, tên bé, tiêu đề và nội dung tin nhắn.
12. `getStats()` thống kê câu hỏi, thời gian phản hồi, chủ đề, sản phẩm và đủ 14 ngày kể cả ngày không có câu hỏi.
13. `toMessages()` gom ID sản phẩm của cả lịch sử rồi lấy ảnh và kỹ năng theo lô; `toMessage()` gắn lại lý do riêng của từng câu trả lời.

## Giới hạn và bước tiếp theo

- `ChatbotConfigSeeder` tạo sẵn một cấu hình active (`app.chatbot.default-model`) khi chưa có cấu hình nào.
  Thiếu `GOOGLE_API_KEY` thì app vẫn chạy, chỉ các API cần AI trả 503.
- `app.knowledge.dir` mặc định là `knowledge-files`, nằm ngoài thư mục uploads công khai. TXT/MD cần UTF-8; file tối đa 10 MB.
- Tạo câu trả lời tối đa 25 giây mỗi lần; lỗi 429/503 (model quá tải) được thử lại 1 lần. Embedding câu hỏi 8 giây,
  không retry. `maxTokens` tối thiểu 1024 vì token suy luận của model cũng tính vào giới hạn.
- PDFBox: https://pdfbox.apache.org/3.0/migration.html
- POI: https://poi.apache.org/text-extraction.html
- Google Java SDK: https://github.com/googleapis/java-genai

Các test dùng H2 và Gemini mock; không dùng API key thật, không kiểm tra kết nối đến tài khoản Google.
H2 không có kiểu `vector`: test dùng `support/H2VectorTypeContributor` (lưu vector thành mảng REAL) và
`support/InMemoryVectorSearchRepository` (tính cosine bằng Java, cùng điều kiện lọc và thứ tự như SQL thật). Câu SQL
pgvector thật được kiểm tra ở `PgVectorSearchIntegrationTest`, chỉ chạy khi bật profile, xem `pgvector-setup.md`.

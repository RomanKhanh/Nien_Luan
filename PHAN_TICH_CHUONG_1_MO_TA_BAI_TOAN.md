# Phân tích mã nguồn phục vụ Chương 1 — Mô tả bài toán

**Phạm vi đối chiếu:** nhánh `master`, commit `d696a45` — `Merge branch 'feature/auth-ui'`. Tôi chỉ đọc mã nguồn và tài liệu, không sửa file, không chạy ứng dụng, kiểm thử hoặc gọi dịch vụ bên ngoài.

Bản phân tích phân biệt ba loại bằng chứng:

- **Có triển khai:** đọc được controller, service, dữ liệu và giao diện liên quan.
- **Có kiểm thử tự động:** đọc được test và assertion kiểm tra hành vi. **Chưa xác nhận các test chạy thành công trên commit này.**
- **Đã kiểm chứng tích hợp thực tế:** chưa xác định trong phiên phân tích này, đặc biệt với PostgreSQL, SMTP, Gemini và MoMo sandbox.

Không sử dụng hoặc đưa giá trị bí mật trong `.env` vào kết quả.

## 1. Bài toán và phạm vi hệ thống

Có thể dùng đoạn sau làm cơ sở viết phần giới thiệu bài toán:

> BrainBlocks là website kinh doanh đồ chơi giáo dục STEM, hỗ trợ phụ huynh tìm kiếm và lựa chọn sản phẩm theo độ tuổi, nhóm kỹ năng, giá và danh mục. Ngoài các nghiệp vụ thương mại điện tử, hệ thống quản lý hồ sơ trẻ và các đồ chơi trẻ đang có, tính toán mức độ tác động của các đồ chơi đến từng nhóm kỹ năng, hiển thị biểu đồ và đề xuất sản phẩm bổ sung. Chatbot sử dụng dữ liệu sản phẩm, hồ sơ trẻ và tài liệu tri thức do quản trị viên cung cấp để hỗ trợ tư vấn bằng ngôn ngữ tự nhiên. Quản trị viên quản lý sản phẩm, đơn hàng, khách hàng, phản hồi, kho tri thức, cấu hình chatbot và thống kê.

**Giới hạn cần thể hiện ngay trong Chương 1:**

- Điểm kỹ năng là **chỉ số suy ra từ đồ chơi**, không phải kết quả đo năng lực thực tế của trẻ.
- “Lộ trình” hiện tại là biểu đồ dựng lại từ các lần thêm đồ chơi đang còn trong hồ sơ; chưa phải kế hoạch giáo dục có bài học, thời lượng, mục tiêu và kết quả thực hiện.
- Hệ thống có quy trình yêu cầu đổi/trả, nhưng chưa triển khai đầy đủ nghiệp vụ logistics đổi hàng và hoàn tiền tự động.
- Không xây dựng hoặc huấn luyện mô hình AI riêng; sử dụng Gemini.

Căn cứ: [đề bài](D:/Nien_Luan/CLAUDE.local.md), `SkillProfileService.getTimeline`, `SkillScoreCalculator.score`, `ChatService.sendMessage`, `GeminiService.generateAnswer`.

---

## 2. Tác nhân và quyền truy cập

### 2.1. Các tác nhân

| Tác nhân | Chức năng được thực hiện theo mã nguồn |
|---|---|
| **Khách chưa đăng nhập** | Xem trang chủ, danh sách/chi tiết sản phẩm, danh mục, nhóm kỹ năng, đánh giá công khai; tìm kiếm, lọc, sắp xếp, nhận gợi ý từ khóa; ghi nhận lượt xem sản phẩm; xem chính sách; đăng ký, yêu cầu mã xác minh email, đăng nhập, quên/đặt lại mật khẩu; truy cập trang kết quả MoMo để kiểm tra thông tin trả về. |
| **CUSTOMER** | Các chức năng công khai; quản lý thông tin cá nhân và mật khẩu; quản lý hồ sơ trẻ, đồ chơi đã có, hồ sơ/biểu đồ kỹ năng và gợi ý; quản lý giỏ hàng, báo phí vận chuyển, chọn voucher, đặt/xem/hủy đơn; thanh toán MoMo và xem trạng thái; đánh giá; gửi và theo dõi phản hồi, khiếu nại, yêu cầu hủy/đổi/trả; tải bằng chứng của mình; trò chuyện và quản lý hội thoại của mình; xem/đánh dấu thông báo. |
| **ADMIN** | Các chức năng công khai; cập nhật tài khoản cá nhân, đổi mật khẩu; quản lý khách hàng và khóa/mở khóa; sản phẩm, ảnh, tồn kho, danh mục, kỹ năng; xem và cập nhật đơn; kiểm duyệt đánh giá; xử lý khiếu nại và bằng chứng; xem voucher và thống kê voucher; quản lý tài liệu tri thức, cấu hình chatbot; giám sát hội thoại và thống kê; xem/đánh dấu thông báo của mình. |
| **Dịch vụ SMTP** | Nhận yêu cầu gửi mã đăng ký và liên kết đặt lại mật khẩu từ backend. |
| **Google Gemini** | Tạo embedding tài liệu/câu hỏi và tạo câu trả lời theo ngữ cảnh backend gửi. |
| **MoMo sandbox** | Nhận yêu cầu tạo thanh toán, cung cấp `payUrl`, chuyển trình duyệt về trang kết quả và gửi IPN đến backend. |
| **Bộ lập lịch/worker nội bộ** | Hủy đơn MoMo chưa thanh toán quá hạn; đóng yêu cầu đổi/trả quá hạn; dọn bằng chứng; lập chỉ mục tài liệu và tiếp tục công việc còn dang dở khi khởi động. Đây là thành phần nội bộ, không phải người dùng bên ngoài. |

**Không có tác nhân “trẻ em” đăng nhập riêng.** Trẻ là đối tượng dữ liệu do CUSTOMER quản lý.

**Không thấy tích hợp API đơn vị vận chuyển.** Phí vận chuyển được tính nội bộ; trạng thái giao hàng do ADMIN cập nhật. Dữ liệu địa chỉ được đọc từ tài nguyên JSON, không gọi API địa chính trong lúc vận hành.

### 2.2. Đối chiếu frontend và backend

| Phạm vi | Frontend | Backend | Kết luận |
|---|---|---|---|
| Trang công khai | `App` không bọc `RequireAuth` | `SecurityConfig.securityFilterChain` mở GET sản phẩm/danh mục/kỹ năng/địa chỉ và POST lượt xem | Phù hợp. |
| Hồ sơ cá nhân | `/account` yêu cầu đăng nhập, không buộc CUSTOMER | `/api/me` yêu cầu xác thực | CUSTOMER và ADMIN đều dùng được. Địa chỉ mặc định chỉ áp dụng cho CUSTOMER. |
| Hồ sơ trẻ, checkout, đơn, voucher, phản hồi | `RequireAuth role="CUSTOMER"` | Controller có `@PreAuthorize("hasRole('CUSTOMER')")` | Phù hợp. |
| Giỏ hàng | `/cart` là route công khai, nhưng `useCart` chỉ gọi API khi CUSTOMER; thêm hàng yêu cầu đăng nhập | `CartController` chỉ CUSTOMER | Route công khai không đồng nghĩa khách có giỏ hàng. Không có giỏ khách lưu cục bộ trong luồng đã đọc. |
| Chatbot | `/chat` là route công khai, `ChatPanel` hiện cổng đăng nhập hoặc thông báo dành cho CUSTOMER | `ChatController` và `ChatService` chỉ CUSTOMER | Khách nhìn thấy trang/khung chat nhưng chưa được gửi câu hỏi. ADMIN không dùng chat khách hàng. |
| Quản trị | `RequireAuth role="ADMIN"` | `/api/admin/**` yêu cầu ADMIN | Backend là nơi thực thi quyền; chỉnh role trong trình duyệt không cấp quyền API. |
| Thông báo | Route `/notifications` chỉ CUSTOMER; ADMIN có giao diện thông báo trong khu vực quản trị | `NotificationController` yêu cầu xác thực, không giới hạn CUSTOMER | ADMIN vẫn được dùng API thông báo của chính mình. |
| MoMo return/IPN | Trang return công khai; đọc trạng thái đơn cần CUSTOMER | Hai callback công khai; tạo URL/xem Payment chỉ CUSTOMER và phải sở hữu đơn | Callback được bảo vệ bằng chữ ký, không bằng JWT. |

Căn cứ chính:

- [SecurityConfig.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/config/SecurityConfig.java): `securityFilterChain`, `corsConfigurationSource`.
- [App.tsx](D:/Nien_Luan/frontend/src/App.tsx): `App`.
- [RequireAuth.tsx](D:/Nien_Luan/frontend/src/auth/RequireAuth.tsx): `RequireAuth`.
- [useCart.ts](D:/Nien_Luan/frontend/src/features/cart/useCart.ts): `useCart`, `useAddToCart`.
- [ChatPanel.tsx](D:/Nien_Luan/frontend/src/features/chat/ChatPanel.tsx): `ChatPanel`.
- [PaymentController.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/controller/payment/PaymentController.java): `createUrl`, `momoReturn`, `momoIpn`, `getStatus`.

---

## 3. Danh sách chức năng và dữ liệu đặc tả use case

Các bảng dưới đây đồng thời là danh sách chức năng hiện có và dữ liệu để chuyển sang mẫu đặc tả use case của báo cáo.

**Quy ước:**

- **K:** khách chưa đăng nhập; **C:** CUSTOMER; **A:** ADMIN.
- **Tiền:** tiền điều kiện; **Hậu:** hậu điều kiện khi thành công.
- Các bước `1 → 2 → 3` là luồng chính.
- `A1`, `A2` được đánh số trong phạm vi từng dòng.
- Với API bảo vệ, thiếu xác thực dẫn đến `401`, sai vai trò dẫn đến `403`; đây là luồng phụ chung, kết thúc trước nghiệp vụ.
- Các use case CRUD được ghi rõ từng thao tác trong cùng dòng để tránh lặp đặc tả giống nhau.

### 3.1. Tài khoản

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC01 — Yêu cầu mã xác minh email.** Gửi mã phục vụ đăng ký. | **K**. Kích hoạt: bấm gửi mã. **Tiền:** email hợp lệ, chưa đăng ký; chức năng gửi mail sẵn sàng. **Hậu:** mã mới được lưu dạng băm trong bộ nhớ, yêu cầu gửi thư được thực hiện. | **1.** Người dùng nhập email. **2.** Hệ thống chuẩn hóa, kiểm tra trùng và thời gian chờ. **3.** Tạo mã sáu số, thay mã cũ và gửi email bất đồng bộ. **4.** Giao diện thông báo và đếm thời gian gửi lại. | **A1:** email đã tồn tại → báo lỗi; quay bước 1. **A2:** chưa hết cooldown → từ chối; chờ rồi quay bước 2. **A3:** thiếu cấu hình SMTP → `503`, kết thúc. **A4:** SMTP lỗi sau khi đã nhận yêu cầu → ghi log; chưa có xác nhận email thực sự đến hộp thư. |
| **UC02 — Đăng ký tài khoản.** Tạo CUSTOMER. | **K**. Kích hoạt: gửi form đăng ký. **Tiền:** dữ liệu hợp lệ, email chưa dùng, mã hợp lệ nếu bật xác minh. **Hậu:** tạo CUSTOMER, giỏ hàng và voucher chào mừng. | **1.** Nhập tên, email, mã, mật khẩu và xác nhận. **2.** Frontend kiểm tra dữ liệu. **3.** Backend kiểm tra email và tiêu thụ mã. **4.** Băm mật khẩu, tạo tài khoản/giỏ/voucher trong transaction. **5.** Frontend gọi đăng nhập và chuyển trang. | **A1:** dữ liệu/mã sai hoặc hết hạn → sửa và quay bước 1. **A2:** email trùng → lỗi xung đột, quay bước 1. **A3:** đăng ký thành công nhưng đăng nhập tiếp theo lỗi → tài khoản đã tồn tại; không rollback đăng ký. **A4:** lỗi DB sau tiêu thụ mã → mã trong bộ nhớ không thuộc transaction DB; có thể phải xin mã mới. |
| **UC03 — Đăng nhập.** Xác thực và mở phiên bằng JWT. | **K**. Kích hoạt: gửi email/mật khẩu. **Tiền:** tài khoản tồn tại, đang bật. **Hậu:** nhận JWT, role và user ID; frontend lưu phiên. | **1.** Nhập thông tin. **2.** Backend chuẩn hóa email và xác thực mật khẩu. **3.** Nếu ADMIN, cập nhật lần đăng nhập gần nhất. **4.** Phát JWT. **5.** Frontend lưu phiên và điều hướng. | **A1:** sai thông tin → báo lỗi, quay bước 1. **A2:** tài khoản khóa → từ chối, kết thúc. |
| **UC04 — Đăng xuất.** Kết thúc phiên tại trình duyệt. | **C/A**. Kích hoạt: bấm đăng xuất. **Tiền:** có phiên frontend. **Hậu:** xóa phiên và cache truy vấn tại client. | **1.** Người dùng chọn đăng xuất. **2.** Frontend xóa token lưu cục bộ, session và Query cache. **3.** Giao diện trở về trạng thái chưa đăng nhập. | **A1:** storage không sử dụng được → vẫn xóa phiên trong bộ nhớ. **Giới hạn:** không thấy API thu hồi JWT khi đăng xuất; JWT đã sao chép vẫn có thể còn hiệu lực. |
| **UC05 — Xem/cập nhật thông tin cá nhân và địa chỉ mặc định.** | **C/A**. Kích hoạt: mở tài khoản hoặc lưu form. **Tiền:** đăng nhập. **Hậu:** trả/cập nhật tên, điện thoại; C được cập nhật địa chỉ mặc định. | **1.** Mở trang, hệ thống tải hồ sơ hiện tại. **2.** Người dùng sửa thông tin. **3.** Backend kiểm tra dữ liệu và quan hệ địa chỉ. **4.** Lưu và trả hồ sơ mới. | **A1:** địa chỉ thiếu/sai quan hệ → báo lỗi, quay bước 2. **A2:** C bỏ trống toàn bộ địa chỉ → xóa địa chỉ mặc định, kết thúc thành công. **A3:** A gửi trường địa chỉ → backend bỏ qua phần địa chỉ. |
| **UC06 — Đổi mật khẩu.** | **C/A**. Kích hoạt: gửi mật khẩu hiện tại và mật khẩu mới. **Tiền:** đăng nhập; mật khẩu hiện tại đúng. **Hậu:** lưu mật khẩu mới, tăng `tokenVersion`, JWT cũ mất hiệu lực. | **1.** Nhập mật khẩu hiện tại/mới. **2.** Backend khóa dòng tài khoản và kiểm tra. **3.** Băm/lưu mật khẩu mới, tăng phiên bản token. **4.** Người dùng cần đăng nhập lại. | **A1:** mật khẩu hiện tại sai → quay bước 1. **A2:** mật khẩu mới giống hiện tại → từ chối, quay bước 1. **A3:** mật khẩu mới không hợp lệ → quay bước 1. |
| **UC07 — Yêu cầu đặt lại mật khẩu.** | **K**. Kích hoạt: gửi email ở trang quên mật khẩu. **Tiền:** tài khoản tồn tại, không khóa, mail sẵn sàng. **Hậu:** yêu cầu gửi liên kết đặt lại. | **1.** Nhập email. **2.** Backend kiểm tra tài khoản và cooldown. **3.** Tạo token đặt lại chứa email/phiên bản token. **4.** Gửi liên kết qua SMTP bất đồng bộ. | **A1:** email không tồn tại → `404`, quay bước 1. **A2:** tài khoản khóa → `403`, kết thúc. **A3:** cooldown/SMTP thiếu → từ chối; chờ hoặc cấu hình rồi thử lại. **A4:** gửi SMTP lỗi bất đồng bộ → chỉ ghi log. |
| **UC08 — Đặt mật khẩu mới qua liên kết.** | **K**, SMTP hỗ trợ cung cấp liên kết. Kích hoạt: mở link và gửi mật khẩu mới. **Tiền:** token đúng chữ ký, còn hạn, tài khoản đang bật và phiên bản khớp. **Hậu:** đổi mật khẩu, vô hiệu link/JWT cũ. | **1.** Mở link, nhập và xác nhận mật khẩu mới. **2.** Backend xác thực token. **3.** Khóa tài khoản và kiểm tra lại phiên bản. **4.** Lưu mật khẩu, tăng `tokenVersion`. **5.** Frontend xóa phiên hiện tại và chuyển đăng nhập. | **A1:** link thiếu/sai/hết hạn/đã dùng → kết thúc, giao diện hướng tới xin link mới. **A2:** mật khẩu không hợp lệ → quay bước 1. **A3:** dùng link đồng thời → request sau bị từ chối sau khi phiên bản tăng. |

**Căn cứ:** [AuthService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/AuthService.java), `register`, `login`; [EmailVerificationService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/auth/EmailVerificationService.java), `sendCode`, `verify`; [PasswordResetService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/auth/PasswordResetService.java), `requestReset`, `resetPassword`; [UserService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/user/UserService.java), `getMyProfile`, `updateMyProfile`, `changeMyPassword`; [AuthContext.tsx](D:/Nien_Luan/frontend/src/auth/AuthContext.tsx), `login`, `logout`; [RegisterPage.tsx](D:/Nien_Luan/frontend/src/pages/auth/RegisterPage.tsx), `sendCode`, `onSubmit`.

### 3.2. Sản phẩm, tìm kiếm và bộ lọc

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC09 — Tra cứu danh sách sản phẩm.** Tìm, lọc, sắp xếp, phân trang. | **K/C/A**. Kích hoạt: mở danh sách hoặc đổi bộ lọc. **Tiền:** tham số hợp lệ. **Hậu:** trả trang sản phẩm đang bán phù hợp điều kiện. | **1.** Chọn từ khóa, danh mục, khoảng tuổi, kỹ năng, giá, còn hàng và kiểu sắp xếp. **2.** Frontend gửi API. **3.** Backend xây điều kiện truy vấn, sắp xếp và lấy trang. **4.** Trả thông tin, ảnh, tác động kỹ năng và đánh giá tổng hợp. | **A1:** không khớp → danh sách rỗng, quay bước 1. **A2:** khoảng giá sai/sort sai/phân trang sai → lỗi, sửa và quay bước 1. |
| **UC10 — Nhận gợi ý khi gõ tìm kiếm.** | **K/C/A**. Kích hoạt: nhập ô tìm kiếm. **Tiền:** có từ khóa. **Hậu:** trả gợi ý sản phẩm/danh mục/kỹ năng. | **1.** Người dùng gõ. **2.** Hệ thống chuẩn hóa chữ, tìm và xếp hạng tên khớp. **3.** Hiển thị gợi ý. **4.** Người dùng chọn để mở sản phẩm hoặc áp bộ lọc. | **A1:** từ khóa rỗng/không khớp → không có gợi ý, tiếp tục bước 1. |
| **UC11 — Xem chi tiết và đánh giá công khai.** | **K/C/A**. Kích hoạt: mở sản phẩm. **Tiền:** sản phẩm tồn tại và active. **Hậu:** hiển thị chi tiết; lượt xem được tăng nếu API ghi nhận thành công. | **1.** Mở sản phẩm. **2.** Hệ thống tải ảnh, mô tả, giá, tuổi, kỹ năng, video và thống kê đánh giá. **3.** Tải đánh giá visible. **4.** Frontend gửi ghi nhận lượt xem; lưu thông tin xem gần đây tại client. | **A1:** sản phẩm ẩn/không tồn tại → `404`, kết thúc. **A2:** không có đánh giá → hiển thị trạng thái chưa có đánh giá. |

**Căn cứ:** [ProductService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/ProductService.java), `search`, `getActiveProduct`, `recordView`, `toDetailResponse`; [ProductSpecifications.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/ProductSpecifications.java), `matching`, `relevance`; [SearchSuggestionService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/SearchSuggestionService.java), `suggest`; [ProductListPage.tsx](D:/Nien_Luan/frontend/src/pages/ProductListPage.tsx), `ProductListPage`; [RecentlyViewed.tsx](D:/Nien_Luan/frontend/src/features/catalog/RecentlyViewed.tsx).

### 3.3. Hồ sơ trẻ, đồ chơi và kỹ năng

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC12 — Xem danh sách/chi tiết hồ sơ trẻ.** | **C**. Kích hoạt: mở mục hồ sơ trẻ. **Tiền:** đăng nhập; hồ sơ chi tiết thuộc C. **Hậu:** trả hồ sơ của khách. | **1.** Mở danh sách. **2.** Hệ thống lấy hồ sơ theo customer hiện tại. **3.** Chọn trẻ, hệ thống trả tên, ngày sinh, tuổi, giới tính, ghi chú và nhóm quan tâm. | **A1:** chưa có trẻ → danh sách rỗng. **A2:** hồ sơ không tồn tại/không sở hữu → `404`, kết thúc. |
| **UC13 — Tạo/sửa/xóa hồ sơ trẻ.** | **C**. Kích hoạt: chọn thêm, lưu chỉnh sửa hoặc xác nhận xóa. **Tiền:** dữ liệu hợp lệ; sửa/xóa phải sở hữu. **Hậu:** tạo/cập nhật/xóa hồ sơ. | **1.** Chọn thao tác. **2.** Với tạo/sửa, nhập dữ liệu và nhóm quan tâm. **3.** Backend kiểm tra và lưu; tạo mới đồng thời khởi tạo hồ sơ kỹ năng. **4.** Với xóa, gỡ tham chiếu trong chat/đơn rồi xóa dữ liệu hồ sơ phụ thuộc. | **A1:** ngày sinh/tên/kỹ năng sai → sửa, quay bước 2. **A2:** không sở hữu → `404`, kết thúc. **A3:** PUT không gửi nhóm quan tâm → bỏ toàn bộ nhóm quan tâm hiện có. |
| **UC14 — Xem/thêm/gỡ đồ chơi đã có.** | **C**. Kích hoạt: mở danh sách đồ chơi, chọn thêm hoặc gỡ. **Tiền:** sở hữu trẻ; sản phẩm thêm phải active. **Hậu:** thêm nguồn `MANUAL` hoặc gỡ liên kết; tính lại điểm. | **1.** Mở đồ chơi của trẻ. **2.** Chọn sản phẩm để thêm hoặc món để gỡ. **3.** Backend kiểm tra quyền và trùng lặp. **4.** Lưu/gỡ `ChildProduct`. **5.** Tính lại hồ sơ kỹ năng. | **A1:** đã có sản phẩm → báo lỗi, chọn lại ở bước 2. **A2:** sản phẩm ẩn/không tồn tại → `404`. **A3:** ID món không thuộc trẻ → `404`. |
| **UC15 — Xem/tính lại hồ sơ và biểu đồ kỹ năng.** | **C**. Kích hoạt: mở hồ sơ kỹ năng hoặc yêu cầu tính lại. **Tiền:** sở hữu trẻ. **Hậu:** trả điểm, tỷ lệ, nhóm mạnh/yếu; tính lại nếu được yêu cầu. | **1.** Chọn trẻ. **2.** Backend lấy hồ sơ; nếu dữ liệu cũ chưa có thì tạo từ đồ chơi hiện có. **3.** Frontend hiển thị biểu đồ radar và phân bố. **4.** Khi tính lại, backend tổng hợp impact và cập nhật điểm. | **A1:** chưa có đồ chơi → điểm bằng 0; chưa có cơ sở xác định nhóm yếu. **A2:** không sở hữu → `404`, kết thúc. |
| **UC16 — Xem lộ trình kỹ năng.** | **C**. Kích hoạt: mở biểu đồ lộ trình. **Tiền:** sở hữu trẻ. **Hậu:** trả các mốc cộng dồn theo đồ chơi hiện có. | **1.** Người dùng mở lộ trình. **2.** Backend lấy `ChildProduct` theo thời điểm thêm. **3.** Tính điểm cộng dồn tại mỗi mốc. **4.** Frontend hiển thị biểu đồ. | **A1:** không có món → lộ trình rỗng. **Giới hạn:** món đã gỡ không còn mốc; không lưu lịch sử điểm bất biến. |
| **UC17 — Xem trước tác động một/nhiều sản phẩm.** | **C**. Kích hoạt: chọn sản phẩm hoặc nhóm món dành cho trẻ ở checkout. **Tiền:** sở hữu trẻ. **Hậu:** trả điểm hiện tại, dự kiến và mức tăng; không ghi đồ chơi. | **1.** Chọn trẻ và sản phẩm. **2.** Backend lấy impact hiện tại. **3.** Tính điểm sau bổ sung. **4.** Trả mức tăng để giao diện hiển thị. | **A1:** preview một món đã có → `alreadyOwned`, không có gain. **A2:** preview nhiều món → loại trùng, bỏ món đã có, ẩn hoặc không tồn tại. **A3:** preview một món ẩn/không tồn tại → `404`. |
| **UC18 — Nhận gợi ý sản phẩm tiếp theo.** | **C**. Kích hoạt: mở mục gợi ý của trẻ. **Tiền:** sở hữu trẻ. **Hậu:** trả danh sách xếp hạng, lý do và mức tăng dự kiến. | **1.** Chọn trẻ. **2.** Hệ thống xác định nhóm yếu và nhóm quan tâm. **3.** Lọc sản phẩm active, còn hàng, hợp tuổi, chưa có. **4.** Chấm/xếp hạng và trả lý do. | **A1:** chưa có cơ sở → xếp theo tổng impact. **A2:** không món nào tác động nhóm mục tiêu → fallback tổng impact, kèm ghi chú. **A3:** không còn ứng viên → danh sách rỗng. |
| **UC19 — Tra cứu “Hợp với bé”.** | **C**. Kích hoạt: chọn trẻ tại trang sản phẩm. **Tiền:** sở hữu trẻ. **Hậu:** danh sách hợp tuổi, chưa có, kèm `fitScore`/gain. | **1.** Chọn trẻ và bộ lọc. **2.** Backend thay tuổi lọc bằng tuổi trẻ, loại món đã có. **3.** Với sort `fit`, tính tổng mức tăng và xếp hạng; sort khác dùng tìm kiếm thông thường. **4.** Trả trang kết quả. | **A1:** không khớp → danh sách rỗng, đổi bộ lọc ở bước 1. **A2:** không sở hữu → `404`. **A3:** bỏ chọn trẻ → frontend chuyển về sort công khai phù hợp. |

**Căn cứ:** [ChildProfileService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/child/ChildProfileService.java), `getMyChildren`, `createChild`, `updateChild`, `deleteChild`, `assignProduct`, `removeProduct`; [SkillProfileService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/skill/SkillProfileService.java), `getSkillProfile`, `recalculateFor`, `getTimeline`, `previewProduct`, `previewProducts`; [SkillRecommendationService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/skill/SkillRecommendationService.java), `recommendNext`; [ProductMatchService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/ProductMatchService.java), `match`, `matchByFit`.

### 3.4. Giỏ hàng, đặt hàng, vận chuyển, voucher và thanh toán

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC20 — Xem giỏ hàng và tổng tiền hàng.** | **C**. Kích hoạt: mở giỏ. **Tiền:** có tài khoản/giỏ. **Hậu:** trả các dòng, giá hiện tại, số lượng, tồn kho, active và tổng. | **1.** Mở giỏ. **2.** Backend lấy giỏ theo customer hiện tại. **3.** Tính tổng giá × số lượng. **4.** Hiển thị. | **A1:** chưa đăng nhập → giao diện yêu cầu đăng nhập. **A2:** giỏ rỗng → trạng thái rỗng, kết thúc. |
| **UC21 — Thêm/cập nhật/xóa món hoặc làm trống giỏ.** | **C**. Kích hoạt: thao tác giỏ. **Tiền:** món thuộc giỏ; thêm/sửa phải hợp lệ và đủ hàng. **Hậu:** cập nhật giỏ, chưa trừ kho. | **1.** Chọn sản phẩm/số lượng/thao tác. **2.** Backend kiểm tra active, giới hạn số lượng, tồn kho hoặc quyền sở hữu dòng. **3.** Thêm cộng dồn, sửa số lượng, xóa dòng hoặc xóa toàn bộ. **4.** Trả giỏ mới. | **A1:** vượt 99 hoặc vượt tồn → báo lỗi, quay bước 1. **A2:** sản phẩm ngừng bán → từ chối thêm/sửa; vẫn có thể gỡ. **A3:** dòng không thuộc giỏ → `404`. |
| **UC22 — Chọn địa chỉ và báo phí vận chuyển.** | **C**; K được tra danh sách địa chỉ. Kích hoạt: chọn tỉnh tại checkout. **Tiền:** tỉnh hợp lệ, có giỏ hiện tại. **Hậu:** trả phí/kiện/khối lượng/cảnh báo. | **1.** Chọn tỉnh–huyện–xã và địa chỉ chi tiết. **2.** Frontend tải danh sách cấp con. **3.** Gửi tỉnh để báo giá giỏ hiện tại. **4.** Backend lấy thông số gói hàng và tính phí nội bộ. **5.** Hiển thị phí và tổng dự kiến. | **A1:** mã địa chỉ sai → báo lỗi, chọn lại. **A2:** huyện không có xã → không yêu cầu xã. **A3:** món quá khổ → kiện riêng và cảnh báo; không mặc nhiên từ chối đơn. |
| **UC23 — Xem/chọn voucher.** | **C**. Kích hoạt: mở ví hoặc chọn tại checkout. **Tiền:** voucher thuộc C. **Hậu:** hiển thị trạng thái và giảm dự kiến; chỉ ghi dùng khi tạo đơn. | **1.** Tải voucher. **2.** Người dùng chọn tối đa một freeship và một giảm giá. **3.** Frontend kiểm tra sơ bộ điều kiện. **4.** Backend kiểm tra lại trong tạo đơn và tính giảm. | **A1:** hết hạn/đã dùng/chưa đủ tiền hàng → không áp dụng, chọn lại. **A2:** voucher người khác → `404`. **A3:** loại voucher sai vị trí → từ chối. |
| **UC24 — Đặt hàng.** | **C**. Kích hoạt: xác nhận checkout. **Tiền:** giỏ không rỗng, đủ kho, địa chỉ hợp lệ, phương thức COD/MOMO; trẻ/voucher phải thuộc C. **Hậu:** tạo đơn `PENDING`, chốt giá/phí/giảm, trừ kho, dùng voucher, xóa giỏ, báo ADMIN. | **1.** Nhập người nhận/địa chỉ, chọn thanh toán/voucher và trẻ cho từng món nếu cần. **2.** Frontend gửi yêu cầu. **3.** Backend khóa giỏ, kiểm tra phân công trẻ và địa chỉ. **4.** Kiểm tra/trừ kho, chốt giá, tính lại ship/voucher. **5.** Lưu đơn và làm trống giỏ. **6.** COD mở đơn; MOMO tiếp tục tạo URL thanh toán. | **A1:** giỏ rỗng/ẩn/thiếu kho → rollback, sửa giỏ rồi quay bước 1. **A2:** phí khác `expectedShippingFee` → `409`, rollback; tải lại và người dùng xác nhận lại. **A3:** phân công sai/trùng trẻ hoặc voucher không hợp lệ → rollback. **A4:** tạo đơn thành công nhưng tạo URL MoMo lỗi → giữ đơn, chuyển trang đơn để thử thanh toán lại. |
| **UC25 — Xem lịch sử/chi tiết đơn.** | **C**. Kích hoạt: mở đơn hàng. **Tiền:** đơn thuộc C. **Hậu:** hiển thị dòng hàng, tiền, địa chỉ, trạng thái, trẻ được gắn và hạn thanh toán nếu có. | **1.** Mở danh sách. **2.** Backend lấy đơn theo customer. **3.** Chọn đơn và kiểm tra quyền. **4.** Hiển thị chi tiết/trạng thái. | **A1:** chưa có đơn → danh sách rỗng. **A2:** đơn không tồn tại/người khác → `404`. |
| **UC26 — Tự hủy đơn chờ xác nhận.** | **C**. Kích hoạt: bấm hủy đơn. **Tiền:** sở hữu đơn `PENDING`. **Hậu:** `CANCELLED`, hoàn kho/voucher, đưa món active về giỏ và báo ADMIN. | **1.** Chọn hủy. **2.** Backend khóa đơn và kiểm tra trạng thái. **3.** Cộng món về giỏ trong giới hạn. **4.** Hoàn kho/voucher, đánh dấu khách tự hủy. **5.** Trả trạng thái mới. | **A1:** đã ra khỏi `PENDING` → từ chối, kết thúc. **A2:** sản phẩm đã ẩn → không đưa về giỏ nhưng vẫn hoàn kho. **A3:** giỏ chạm 99 → số lượng đưa về bị giới hạn. |
| **UC27 — Thanh toán/thanh toán lại bằng MoMo.** | **C**, **MoMo**. Kích hoạt: sau đặt đơn hoặc bấm thanh toán. **Tiền:** đơn thuộc C, phương thức MOMO, chưa hủy/chưa SUCCESS/chưa quá hạn áp dụng. **Hậu:** nhận URL và chuyển sang MoMo; chưa tự xác nhận thanh toán. | **1.** Yêu cầu URL. **2.** Backend kiểm tra đơn, tạo/lấy Payment và đặt amount bằng tổng đơn. **3.** Ký yêu cầu và gửi MoMo. **4.** Nhận `payUrl`. **5.** Frontend chuyển trình duyệt đến MoMo. | **A1:** COD/hủy/đã trả/quá hạn → từ chối, kết thúc. **A2:** MoMo lỗi → `503`, đơn vẫn tồn tại, thử lại sau. **A3:** người dùng hủy tại MoMo → đơn chưa mặc nhiên bị hủy. |
| **UC28 — Tiếp nhận kết quả thanh toán và tra cứu.** | **MoMo**, **C**. Kích hoạt: IPN hoặc trình duyệt quay về. **Tiền:** IPN có chữ ký hợp lệ và tìm được đơn/Payment. **Hậu:** IPN cập nhật Payment; return chỉ phục vụ hiển thị. | **1.** MoMo gửi IPN. **2.** Backend xác thực chữ ký, khóa đơn, kiểm tra Payment và số tiền khi thành công. **3.** Thành công: lưu SUCCESS/mã giao dịch/thời điểm, đổi `PENDING → CONFIRMED`; thất bại: FAILED. **4.** Trang return kiểm tra chữ ký và hỏi lại trạng thái từ backend. | **A1:** chữ ký sai/không có đơn/Payment/sai tiền → không ghi SUCCESS. **A2:** Payment đã SUCCESS → trả thành công, không cập nhật lại. **A3:** trả tiền sau khi đơn đã hủy → ghi SUCCESS, giữ CANCELLED và báo hoàn tiền thủ công. **A4:** IPN chưa tới → frontend chờ rồi thông báo chưa xác nhận; không kết luận đã trả chỉ từ URL. |
| **UC29 — Tự hủy đơn MoMo quá hạn.** | Bộ lập lịch nội bộ. Kích hoạt: kiểm tra định kỳ. **Tiền:** MOMO, `PENDING`, đủ thời gian từ lúc đặt. **Hậu:** hủy, hoàn kho/voucher, trả món về giỏ, báo khách. | **1.** Job tìm ID quá hạn. **2.** Khóa từng đơn và kiểm tra lại. **3.** Nếu vẫn chưa thanh toán, trả giỏ/hoàn kho/voucher và hủy. **4.** Tạo thông báo. | **A1:** callback vừa đổi trạng thái → bỏ qua, kết thúc cho đơn đó. |

**Căn cứ:** [CartService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/cart/CartService.java), `getMyCart`, `addItem`, `updateItemQuantity`, `removeItem`, `clearCart`; [OrderService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/order/OrderService.java), `createOrder`, `cancelOrder`, `cancelIfUnpaidExpired`; [ShippingCalculator.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/shipping/ShippingCalculator.java), `quote`, `baseFee`, `pack`; [VoucherService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/voucher/VoucherService.java), `select`, `markUsed`, `releaseFor`; [PaymentService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/payment/PaymentService.java), `createPaymentUrl`, `handleCallback`, `getStatus`; [CheckoutPage.tsx](D:/Nien_Luan/frontend/src/pages/cart/CheckoutPage.tsx), `CheckoutPage`; [MomoReturnPage.tsx](D:/Nien_Luan/frontend/src/pages/payment/MomoReturnPage.tsx), `MomoReturnPage`.

### 3.5. Đánh giá, khiếu nại, hủy/đổi/trả

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC30 — Đánh giá sản phẩm.** | **C**. Kích hoạt: gửi đánh giá. **Tiền:** sản phẩm active, từng có trong đơn DELIVERED của C, chưa đánh giá sản phẩm này. **Hậu:** lưu đánh giá và báo ADMIN. | **1.** Chọn 1–5 sao, nhập nhận xét tùy chọn. **2.** Backend kiểm tra mua hàng và trùng đánh giá. **3.** Lưu Review. **4.** Giao diện cập nhật. | **A1:** chưa nhận hàng → từ chối. **A2:** đã đánh giá → từ chối; không có luồng sửa/xóa đánh giá khách trong service đã đọc. **A3:** rating/comment sai → sửa rồi quay bước 1. |
| **UC31 — Xem đánh giá của mình.** | **C**. Kích hoạt: mở phản hồi. **Tiền:** đăng nhập. **Hậu:** trả Review của C, gồm cờ visible. | **1.** Mở mục đánh giá. **2.** Backend lọc theo customer. **3.** Hiển thị danh sách. | **A1:** chưa có → danh sách rỗng. |
| **UC32 — Gửi phản hồi/yêu cầu hủy/đổi/trả/khiếu nại chất lượng.** | **C**. Kích hoạt: mở form trên đơn. **Tiền:** sở hữu đơn; loại yêu cầu hợp trạng thái; đơn không do khách tự hủy; đổi/trả có video mở hàng. **Hậu:** tạo Complaint PENDING, các dòng/bằng chứng và báo ADMIN. | **1.** Chọn loại, nhập nội dung, chọn dòng/số lượng tùy chọn. **2.** Đính kèm bằng chứng nếu cần. **3.** Backend kiểm tra trạng thái, dòng hàng, số lượng và toàn bộ file. **4.** Lưu yêu cầu/file. **5.** Trả yêu cầu mới. | **A1:** loại không hợp trạng thái/tự hủy → từ chối. **A2:** thiếu video/sai file/quá giới hạn → sửa, quay bước 2. **A3:** dòng sai/trùng/số lượng vượt mua → quay bước 1. **A4:** không chọn dòng → yêu cầu chung cả đơn; không tự suy ra tất cả món được trả. |
| **UC33 — Theo dõi phản hồi và tải bằng chứng.** | **C**; **A** tải được bằng chứng qua quản trị. Kích hoạt: mở yêu cầu/tải file. **Tiền:** C sở hữu hoặc A; file chưa bị dọn/mất. **Hậu:** hiển thị phản hồi, trạng thái, deadline hoặc trả file. | **1.** Mở danh sách yêu cầu. **2.** Hệ thống trả trạng thái, phản hồi ADMIN và thông tin bằng chứng. **3.** Chọn file. **4.** Backend kiểm tra quyền và trả nội dung. | **A1:** không có quyền → `404`. **A2:** file đã dọn/mất → `404`; metadata vẫn có thể còn. |
| **UC34 — Tiếp nhận/từ chối/giải quyết yêu cầu.** | **A**. Kích hoạt: đổi trạng thái và gửi phản hồi. **Tiền:** chuyển trạng thái hợp lệ; đóng phải có phản hồi. **Hậu:** cập nhật trạng thái/người xử lý/thời điểm và báo khách. | **1.** Xem yêu cầu và bằng chứng. **2.** Chọn trạng thái, nhập phản hồi. **3.** Backend kiểm tra chuyển trạng thái/deadline. **4.** Nếu giải quyết CANCEL thì hủy đơn; nếu RETURN thì xem xét gỡ món PURCHASED đã trả hết khỏi trẻ và tính lại. **5.** Lưu và thông báo. | **A1:** chuyển sai/thiếu phản hồi → quay bước 2. **A2:** CANCEL khi đơn đã giao đi → từ chối duyệt; có thể xử lý từ chối yêu cầu. **A3:** quá hạn gửi trả → không cho RESOLVED; job đóng quá hạn. **Giới hạn:** RESOLVED không tự gọi hoàn tiền, nhập kho trả hoặc tạo đơn đổi. |
| **UC35 — Đóng quá hạn và quản lý thời gian giữ bằng chứng.** | Job nội bộ; **A** bật/tắt giữ lại. Kích hoạt: lịch hoặc thao tác hold. **Tiền:** đổi/trả PROCESSING quá hạn; hoặc yêu cầu đóng đủ thời gian lưu file. **Hậu:** REJECTED quá hạn; hoặc dọn file nhưng giữ metadata. | **1.** Job tìm yêu cầu/file đủ điều kiện. **2.** Đổi/trả quá hạn được REJECTED và đánh dấu `returnExpired`. **3.** Bằng chứng hết thời gian giữ và không hold được xóa, ghi `purgedAt`. **4.** ADMIN có thể đổi `evidenceHold` để giữ/bỏ giữ. | **A1:** hold hoặc yêu cầu chưa đóng → không dọn. **A2:** file đã mất → thao tác dọn không cần tạo lại file. **Giới hạn:** chưa thấy ghi nhận mã vận đơn/biên nhận gửi trả làm điều kiện dừng deadline. |

**Căn cứ:** [ReviewService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/review/ReviewService.java), `createReview`, `getMyReviews`; [ComplaintService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/complaint/ComplaintService.java), `createComplaint`, `getMyComplaints`, xử lý lấy bằng chứng; [AdminComplaintService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/complaint/AdminComplaintService.java), `updateStatus`, `removeReturnedProductsFromChildren`, `expireOverdueReturns`, `purgeExpiredEvidence`, `setEvidenceHold`; [ComplaintModal.tsx](D:/Nien_Luan/frontend/src/features/complaints/ComplaintModal.tsx), `ComplaintForm`.

### 3.6. Chatbot, RAG và kho tri thức

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC36 — Tạo/xem/đổi trẻ/xóa hội thoại; xem câu hỏi gợi ý.** | **C**. Kích hoạt: chọn thao tác chat. **Tiền:** sở hữu hội thoại/trẻ nếu có. **Hậu:** tạo/đọc/cập nhật/xóa session; trả câu hỏi mẫu. | **1.** Mở chat và chọn hội thoại hoặc cuộc mới. **2.** Chọn trẻ tùy chọn. **3.** Backend kiểm tra quyền và thực hiện CRUD. **4.** Xem lịch sử/câu hỏi gợi ý. | **A1:** ID không sở hữu → `404`. **A2:** chưa chọn trẻ → dùng câu hỏi chung. **A3:** xóa → cascade tin nhắn/liên kết gợi ý; kết thúc. Frontend chỉ tạo session mới khi gửi câu đầu tiên. |
| **UC37 — Gửi câu hỏi và nhận tư vấn.** | **C**, **Gemini**. Kích hoạt: gửi câu hỏi. **Tiền:** sở hữu session, câu hỏi hợp lệ, còn hạn mức, có cấu hình active và AI sẵn sàng. **Hậu:** lưu cặp USER/BOT, lý do/sản phẩm/nguồn và thời gian phản hồi. | **1.** Nhập câu hỏi. **2.** Backend kiểm tra hạn mức và lấy snapshot ngữ cảnh. **3.** Truy xuất sản phẩm, hồ sơ và tri thức. **4.** Gọi Gemini tạo JSON. **5.** Kiểm tra kết quả/sản phẩm và ngữ cảnh session. **6.** Lưu cặp tin nhắn, trả giao diện. | **A1:** vượt hạn mức → `429`, chờ rồi gửi lại. **A2:** truy xuất embedding lỗi → bỏ tri thức, tiếp tục bước 4. **A3:** thiếu cấu hình/key hoặc AI lỗi/JSON sai → `503`, không lưu cặp tin nhắn; thử lại sau. **A4:** session đổi trong lúc chờ → `409`, gửi lại. **A5:** sản phẩm AI trả không hợp lệ → loại thẻ sản phẩm. |
| **UC38 — Tải tài liệu và lập chỉ mục.** | **A**, **Gemini**, worker nội bộ. Kích hoạt: upload. **Tiền:** file/tiêu đề hợp lệ. **Hậu:** có tài liệu; thành công thành INDEXED với chunk/vector. | **1.** Nhập tiêu đề, chọn file. **2.** Backend kiểm tra, lưu file riêng và tạo PROCESSING. **3.** Sau commit, worker đọc/tách văn bản. **4.** Gemini tạo embedding theo lô. **5.** Lưu chunk/vector và đổi INDEXED. **6.** Frontend hỏi lại trạng thái. | **A1:** file sai/quá lớn → từ chối trước lưu. **A2:** không đọc được/embedding lỗi → UPLOADED, có thể reindex. **A3:** transaction upload rollback → dọn file mới. **A4:** khởi động lại khi PROCESSING → recovery đưa lại vào worker. |
| **UC39 — Tra cứu/đổi tiêu đề/thay file/reindex/bật tắt/xóa tài liệu.** | **A**. Kích hoạt: thao tác kho tri thức. **Tiền:** tài liệu tồn tại; các thao tác bị chặn phải đợi hết PROCESSING. **Hậu:** metadata/file/chunk/status thay đổi đúng thao tác. | **1.** Tìm/lọc tài liệu, xem các chunk. **2.** Chọn đổi tiêu đề, thay file, reindex, bật/tắt hoặc xóa. **3.** Backend khóa và kiểm tra. **4.** Lưu thay đổi; thay file/reindex chuyển PROCESSING; xóa dọn chunk và file sau commit. | **A1:** đang xử lý → `409`, chờ rồi quay bước 2. **A2:** bật INDEXED khi chưa có đủ embedding → `409`. **A3:** thay file lỗi → không coi nội dung cũ là nội dung file mới. |
| **UC40 — Tạo/sửa/kích hoạt cấu hình chatbot.** | **A**. Kích hoạt: lưu hoặc kích hoạt cấu hình. **Tiền:** tham số hợp lệ. **Hậu:** cấu hình mới inactive; kích hoạt tắt các cấu hình khác. | **1.** Xem cấu hình. **2.** Nhập model, temperature, maxTokens, topK và prompt. **3.** Backend kiểm tra/lưu. **4.** Chọn kích hoạt, backend khóa chung và đặt đúng cấu hình active. | **A1:** tham số sai → sửa ở bước 2. **A2:** ID không tồn tại → `404`. **Giới hạn:** lưu tên model chưa chứng minh model đó được Gemini hỗ trợ hoặc tài khoản được phép dùng. |
| **UC41 — Giám sát chatbot.** | **A**. Kích hoạt: tìm hội thoại hoặc mở thống kê. **Tiền:** ADMIN. **Hậu:** hiển thị hội thoại khách, câu hỏi/chủ đề/sản phẩm phổ biến và thời gian phản hồi đã lưu. | **1.** Nhập từ khóa hoặc mở dashboard chatbot. **2.** Backend tìm session/tổng hợp tin nhắn. **3.** Chọn session để xem nội dung, lý do và nguồn. | **A1:** không có kết quả → danh sách/thống kê rỗng. **Giới hạn:** không thấy chức năng ADMIN chỉnh sửa trực tiếp câu trả lời đã lưu. |

**Căn cứ:** [ChatService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/ChatService.java), `createSession`, `updateSession`, `deleteSession`, `getSuggestions`, `sendMessage`, `prepare`, `saveReply`, `getStats`; [KnowledgeBaseService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/KnowledgeBaseService.java), các method CRUD và `retrieve`; [KnowledgeIndexingWorker.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/KnowledgeIndexingWorker.java), `process`; [ChatbotConfigService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/ChatbotConfigService.java), `createConfig`, `updateConfig`, `activateConfig`.

### 3.7. Quản trị và thống kê

| Use case — mô tả | Actor; kích hoạt; tiền/hậu điều kiện | Luồng chính | Luồng phụ |
|---|---|---|---|
| **UC42 — Tra cứu khách hàng và khóa/mở khóa.** | **A**. Kích hoạt: tìm khách/xem chi tiết/đổi enabled. **Tiền:** người dùng tồn tại; đổi trạng thái không được nhắm ADMIN. **Hậu:** trả hồ sơ hoặc đổi trạng thái khách. | **1.** Tìm theo tên/email. **2.** Xem chi tiết. **3.** Chọn khóa/mở. **4.** Backend cập nhật enabled; JWT filter kiểm tra enabled khi gọi API sau đó. | **A1:** không tồn tại → `404`. **A2:** đổi trạng thái ADMIN → từ chối. **Giới hạn:** chưa có use case đổi role CUSTOMER↔ADMIN. |
| **UC43 — Quản lý sản phẩm và tồn kho.** | **A**. Kích hoạt: tạo/sửa/ẩn/bật hoặc cập nhật kho. **Tiền:** dữ liệu hợp lệ, danh mục/kỹ năng tồn tại. **Hậu:** lưu sản phẩm; thay impact thì tính lại hồ sơ trẻ liên quan. | **1.** Nhập tên/mô tả/giá/tuổi/danh mục/impact/thông số gói/kho. **2.** Backend kiểm tra. **3.** Tạo hoặc sửa; cập nhật kho là gán số lượng mới. **4.** Xóa sản phẩm đặt active=false; bật lại qua sửa. | **A1:** tuổi/impact/danh mục/kỹ năng sai → sửa ở bước 1. **A2:** skill bị lặp → từ chối. **A3:** impact 0 → không lưu dòng tác động. |
| **UC44 — Quản lý ảnh sản phẩm.** | **A**. Kích hoạt: tải/xóa/chọn thumbnail/sắp xếp. **Tiền:** sản phẩm và ảnh thuộc đúng sản phẩm; file hợp lệ. **Hậu:** cập nhật ảnh/thứ tự/thumbnail. | **1.** Chọn thao tác. **2.** Backend kiểm tra file hoặc tập ID ảnh. **3.** Lưu/xóa/đổi thumbnail/thứ tự. **4.** Trả danh sách mới. | **A1:** quá số ảnh/sai loại/quá lớn/sai chữ ký file → từ chối. **A2:** ảnh không thuộc sản phẩm hoặc tập thứ tự không hợp lệ → từ chối. |
| **UC45 — Quản lý danh mục và nhóm kỹ năng.** | **A**. Kích hoạt: thêm/sửa/xóa. **Tiền:** tên/code hợp lệ; xóa không còn bị sử dụng. **Hậu:** cập nhật catalog. | **1.** Xem danh sách. **2.** Nhập dữ liệu hoặc chọn xóa. **3.** Backend kiểm tra trùng và tham chiếu. **4.** Lưu/xóa; skill mới được bổ sung vào hồ sơ ở lần tính lại kế tiếp. | **A1:** tên danh mục/code trùng → sửa. **A2:** danh mục còn sản phẩm hoặc skill còn được dùng → từ chối xóa. **A3:** đổi code skill → từ chối; chỉ sửa tên/mô tả. |
| **UC46 — Quản lý đơn hàng.** | **A**. Kích hoạt: lọc/xem/chuyển trạng thái. **Tiền:** đơn tồn tại; chuyển hợp lệ. **Hậu:** cập nhật trạng thái; hủy hoàn kho/voucher; giao xong ghi món vào trẻ nếu đã gắn. | **1.** Xem/lọc đơn và mở chi tiết. **2.** Chọn trạng thái tiếp theo. **3.** Backend khóa đơn, kiểm tra chuyển trạng thái. **4.** Thực hiện tác động liên quan và thông báo khách. | **A1:** chuyển sai → từ chối. **A2:** giao xong mà dòng không gắn trẻ → không tạo đồ chơi cho trẻ. **A3:** trẻ đã có món → không thêm bản ghi trùng. |
| **UC47 — Kiểm duyệt đánh giá.** | **A**. Kích hoạt: xem/lọc và ẩn/hiện. **Tiền:** Review tồn tại. **Hậu:** đổi visible; không xóa cứng. | **1.** Xem đánh giá. **2.** Chọn ẩn/hiện. **3.** Backend lưu visible. **4.** API công khai chỉ trả đánh giá visible. | **A1:** không tồn tại → `404`. |
| **UC48 — Xem thống kê cửa hàng và voucher.** | **A**. Kích hoạt: mở dashboard/voucher. **Tiền:** ADMIN. **Hậu:** trả số liệu aggregate. | **1.** Mở dashboard. **2.** Backend tổng hợp khách, sản phẩm, kho thấp, trạng thái đơn, tiền hàng/ship, sản phẩm bán/xem nhiều, skill quan tâm, complaint và chat. **3.** Với voucher, lọc trạng thái và xem tổng hợp. | **A1:** không có dữ liệu → trả 0/danh sách rỗng. **Giới hạn:** chưa thấy xuất báo cáo PDF/Excel; voucher quản trị là tra cứu/thống kê, chưa thấy phát voucher tùy ý qua CRUD. |
| **UC49 — Xem/đánh dấu thông báo.** | **C/A**. Kích hoạt: mở chuông/danh sách, đánh dấu đọc. **Tiền:** đăng nhập. **Hậu:** trả thông báo của mình hoặc cập nhật read. | **1.** Tải danh sách/số chưa đọc theo người hiện tại. **2.** Mở thông báo. **3.** Đánh dấu một hoặc tất cả, có thể theo loại. **4.** Cập nhật số chưa đọc. | **A1:** đánh dấu ID của người khác/không tồn tại → không thay đổi, không tiết lộ dữ liệu. |

**Căn cứ:** [AdminUserService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/user/AdminUserService.java), `getCustomers`, `getUser`, `updateStatus`; [AdminProductService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/AdminProductService.java), `createProduct`, `updateProduct`, `updateStock`, `deleteProduct`; [ProductImageService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/ProductImageService.java), `upload`, `delete`, `setThumbnail`, `reorder`; [CategoryService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/product/CategoryService.java); [SkillService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/skill/SkillService.java); [AdminOrderService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/order/AdminOrderService.java), `updateStatus`; [AdminStatsService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/stats/AdminStatsService.java), `getStats`; [NotificationService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/notification/NotificationService.java), `getMyNotifications`, `markRead`, `markAllRead`.

---

## 4. Quy tắc nghiệp vụ quan trọng

### 4.1. Tài khoản và xác minh

| Quy tắc | Hành vi thực tế | Loại |
|---|---|---|
| Email | Chuẩn hóa trước đăng ký/đăng nhập; không cho email đã có đăng ký lại. | Cố định trong luồng nghiệp vụ. |
| Vai trò đăng ký | Luôn tạo `Customer`; không nhận role do client chọn. `User.getRole`/`syncRoleColumn` suy ra role từ lớp con. | Cố định. |
| Mật khẩu đăng ký/đặt lại | Backend kiểm tra 8–72 ký tự. Frontend thêm yêu cầu có chữ cái và chữ số. | Cố định trong validation; **hai phía khác nhau**. |
| Mã đăng ký | Sáu số, lưu SHA-256 gắn email, dùng một lần; mã mới thay mã cũ. | Cố định. |
| Sai mã | Tối đa 5 lần sai; đủ giới hạn thì hủy mã. | Cố định. |
| Hạn mã/cooldown | Mặc định 10 phút/60 giây; có thể đổi cấu hình. Xác minh có thể tắt bằng cấu hình. | Cấu hình. |
| Đăng nhập | Tài khoản phải enabled; mật khẩu đúng; phát JWT. | Cố định. |
| JWT | Mặc định 86.400.000 ms, tương đương 24 giờ. | Cấu hình. |
| Link đặt lại | Mặc định 30 phút; yêu cầu lại mặc định sau 60 giây. Ký bằng khóa riêng suy ra theo mục đích. | Thời hạn cấu hình; cơ chế cố định. |
| Đổi/đặt lại mật khẩu | Tăng `tokenVersion`, vô hiệu JWT và link cũ. | Cố định. |
| Đăng xuất | Xóa token/cache phía client; không tự thu hồi JWT phía server. | Hành vi hiện tại. |

**Giới hạn:** mã và cooldown lưu trong bộ nhớ một instance; khởi động lại làm mất trạng thái. Cooldown reset password dùng `get` rồi `put`, không có thao tác nguyên tử như cooldown gửi mã đăng ký.

Căn cứ: `EmailVerificationService`, `PasswordResetService`, [PasswordResetTokenService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/security/PasswordResetTokenService.java), [JwtService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/security/JwtService.java), [password.ts](D:/Nien_Luan/frontend/src/lib/password.ts).

### 4.2. Điểm kỹ năng và thời điểm ghi nhận đồ chơi

**Công thức cho mỗi nhóm kỹ năng:**

\[
S = 10 \times \left(1-\prod_i\left(1-\alpha p_i/10\right)\right)
\]

Trong đó:

- \(p_i\) là `impactIndex` của **từng sản phẩm khác nhau trẻ đang có**.
- `alpha` mặc định **0,5**, cấu hình được trong khoảng `(0, 1]`.
- Impact được chặn về `[0,10]` trong calculator.
- Điểm làm tròn một chữ số thập phân, `HALF_UP`.
- Tỷ lệ phân bố = điểm nhóm / tổng điểm các nhóm × 100, làm tròn một chữ số; tổng 0 thì tỷ lệ 0.
- Không nhân điểm theo số lượng mua cùng sản phẩm: `ChildProduct` là liên kết duy nhất trẻ–sản phẩm.

**Ghi nhận/cập nhật:**

1. Tạo trẻ → khởi tạo hồ sơ kỹ năng.
2. Thêm thủ công → tạo `MANUAL`, tính lại ngay; không yêu cầu chứng minh đã mua.
3. Checkout gắn trẻ vào `OrderItem` → mới lưu ý định “dành cho”.
4. Chuyển đơn sang `DELIVERED` → tạo `PURCHASED` nếu trẻ chưa có món; tính lại một lần cho mỗi trẻ thay đổi.
5. ADMIN sửa impact sản phẩm → tính lại các trẻ đang có sản phẩm.
6. Khi khởi động → có runner tính lại toàn bộ theo công thức/alpha hiện tại.
7. Giải quyết RETURN → gỡ món `PURCHASED` khi tổng lượng trả đã giải quyết đạt tổng lượng mua đã giao cho trẻ; giữ món `MANUAL`.

**Mức hiển thị:** 0 = Chưa có; `(0,3,5)` = Mới bắt đầu; `[3,5,7)` = Đang phát triển; từ 7 = Phong phú. Các ngưỡng hiện là cố định trong `SkillLevel.of` và frontend.

**Lộ trình không lưu snapshot lịch sử:** thay impact hoặc gỡ món có thể làm thay đổi biểu đồ quá khứ.

Căn cứ: [SkillScoreCalculator.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/skill/SkillScoreCalculator.java), `score`; `SkillProfileService.recalculateFor/getTimeline`; `AdminOrderService.createChildProductsForDeliveredOrder`; `AdminComplaintService.removeReturnedProductsFromChildren`; [SkillLevel.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/skill/SkillLevel.java), `of`.

### 4.3. Tìm kiếm và gợi ý

| Luồng | Lọc và xếp hạng |
|---|---|
| **Tìm kiếm công khai** | Tìm tên+mô tả qua `searchText` chuẩn hóa bỏ dấu. Khoảng tuổi sản phẩm **giao với** khoảng tuổi lọc. Giá có cận bao gồm. Nhiều nhóm kỹ năng dùng điều kiện **ít nhất một nhóm được chọn có impact ≥ 5**, không yêu cầu tất cả nhóm. Chỉ lọc còn hàng nếu bật điều kiện tương ứng. |
| **Sort `relevance` công khai** | Tổng impact vào nhóm kỹ năng đang lọc; không lọc thì tổng mọi nhóm. Bằng điểm ưu tiên ID giảm dần. Đây là độ phù hợp theo chỉ số sản phẩm, không phải cá nhân hóa trẻ. |
| **“Hợp với bé” / `fit`** | Thay khoảng tuổi bằng tuổi trẻ; bỏ món đã có. `fitScore` = tổng gain vào nhóm đang lọc, hoặc tất cả nhóm nếu không lọc. Bằng điểm ưu tiên giá thấp rồi ID. **Không tự buộc còn hàng nếu bộ lọc chưa bật.** |
| **Sản phẩm tiếp theo** | Luôn active, còn hàng, hợp tuổi, chưa có. Điểm = `2 × impact nhóm yếu nhất + tổng impact nhóm quan tâm`. Không có cơ sở thì tổng impact. Bằng điểm: tổng impact cao hơn → giá thấp hơn → ID. |
| **Fallback gợi ý tiếp theo** | Nếu xếp theo mục tiêu không ra kết quả nhưng còn ứng viên, xếp theo tổng impact và thêm ghi chú. Nhánh fallback không có cùng bước loại score 0 như nhánh chính, nên không nên viết “mọi gợi ý luôn có điểm > 0”. |

Nhóm yếu nhất chỉ được xác định khi `totalProducts > 0`; hòa điểm chọn skill ID nhỏ hơn. Lý do gợi ý có thể gồm nhóm yếu, nhóm quan tâm, tuổi phù hợp và gain dự kiến.

Căn cứ: `ProductSpecifications.matching/relevance`, `ProductMatchService.fitScore`, `SkillRecommendationService.score/rank/recommendNext`, [SkillProfile.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/entity/SkillProfile.java), `getWeakestSkill`, `getStrongestSkill`.

### 4.4. Tồn kho và trạng thái đơn hàng

**Tồn kho:**

- Thêm vào giỏ **chưa trừ kho**.
- Trừ kho lúc `OrderService.createOrder`, trước thanh toán.
- Dùng UPDATE kiểm tra và trừ đồng thời; thiếu một dòng thì rollback toàn bộ đơn.
- Hoàn kho khi khách tự hủy, ADMIN hủy, duyệt yêu cầu hủy hoặc job hủy MoMo quá hạn.
- Không thấy hoàn kho tự động khi giải quyết RETURN/EXCHANGE.
- ADMIN cập nhật tồn bằng cách gán số mới; chưa thấy sổ giao dịch nhập/xuất kho riêng.

**Chuyển trạng thái do ADMIN:**

| Trạng thái hiện tại | Trạng thái được chuyển |
|---|---|
| `PENDING` | `CONFIRMED`, `CANCELLED` |
| `CONFIRMED` | `SHIPPING`, `CANCELLED` |
| `SHIPPING` | `DELIVERED` |
| `DELIVERED` | Không có chuyển tiếp |
| `CANCELLED` | Không có chuyển tiếp |

Ngoài ra:

- IPN MoMo thành công tự chuyển `PENDING → CONFIRMED`.
- CUSTOMER chỉ tự hủy `PENDING`.
- CUSTOMER muốn hủy `CONFIRMED` phải gửi Complaint loại CANCEL.
- MOMO còn `PENDING` quá mặc định 24 giờ bị job hủy; kiểm tra mặc định mỗi 10 phút.
- `AdminOrderService.updateStatus` **không kiểm tra Payment SUCCESS trước khi ADMIN xác nhận/giao đơn MOMO**.

Căn cứ: `OrderService.createOrder/cancelOrder/cancelIfUnpaidExpired`, `AdminOrderService.ALLOWED_TRANSITIONS/updateStatus`, [UnpaidOrderPolicy.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/order/UnpaidOrderPolicy.java), `deadline/isExpired`; `ProductRepository.decreaseStock/increaseStock`.

### 4.5. Hủy, đổi, trả, hoàn tiền và đánh giá

| Loại yêu cầu | Điều kiện gửi |
|---|---|
| `CANCEL` | Đơn `CONFIRMED`. |
| `RETURN`, `EXCHANGE`, `QUALITY` | Đơn `DELIVERED`. |
| `OTHER` | Mọi trạng thái, nhưng đơn khách tự hủy bị chặn gửi phản hồi. |

**Đổi/trả:**

- Bắt buộc video mở hàng.
- Có thể không chọn dòng hàng; khi đó là yêu cầu chung, không có danh sách món để logic gỡ đồ chơi xử lý.
- Số lượng từng dòng phải dương, không vượt lượng mua của dòng, không trùng ID dòng.
- Chưa thấy kiểm tra tổng số lượng qua **nhiều yêu cầu trả khác nhau** để bảo đảm không trả vượt tổng mua.
- Chưa thấy thời hạn gửi yêu cầu tính từ ngày nhận hàng.
- Có thời hạn gửi hàng về: mặc định 7 ngày từ khi chuyển `PROCESSING`.
- Chưa thấy trường ghi nhận thực tế gửi/nhận hàng trả; mã quá hạn dựa trạng thái và `acceptedAt`.

**Trạng thái Complaint:**

- Thông thường: `PENDING → PROCESSING/REJECTED`; `PROCESSING → RESOLVED`.
- CANCEL: còn cho `PENDING → RESOLVED` trực tiếp và `PROCESSING → REJECTED`.
- Job có thể chuyển đổi/trả `PROCESSING → REJECTED` vì quá hạn.
- Đóng RESOLVED/REJECTED phải có phản hồi.

**Hoàn tiền:** chưa thấy API refund MoMo hoặc trạng thái hoàn tiền đầy đủ. Luồng thanh toán sau hủy chỉ thông báo ADMIN hoàn thủ công. Hủy một đơn MOMO đã thanh toán bằng thao tác ADMIN chưa có cùng cơ chế thông báo refund như trường hợp callback đến sau hủy.

**Đánh giá:** sản phẩm active, từng mua trong đơn DELIVERED, mỗi customer–product một đánh giá, 1–5 sao. Điều kiện hiện không loại trừ sản phẩm đã trả; ADMIN ẩn/hiện đánh giá thay vì xóa.

Căn cứ: `ComplaintService.ALLOWED_ORDER_STATUSES/createComplaint`; `AdminComplaintService.ALLOWED_TRANSITIONS/CANCEL_TRANSITIONS/updateStatus`; [ReturnPolicy.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/complaint/ReturnPolicy.java); `ReviewService.createReview`; `PaymentService.notifyRefundNeeded`.

### 4.6. Vận chuyển và voucher

**Vận chuyển được tính nội bộ theo miền của tỉnh nhận:**

1. Bung số lượng thành từng món.
2. Xếp kiện theo First Fit Decreasing.
3. Khối lượng tính phí từng kiện = `max(tổng khối lượng thực, tổng thể tích quy đổi)`.
4. Tính phí bậc từng kiện và cộng lại.
5. Cộng phụ phí cồng kềnh một lần cho đơn.
6. Làm tròn phí lên theo bội số cấu hình.

Các giá trị trong `application.properties` hiện tại, **đều là cấu hình**:

| Tham số | Giá trị khai báo |
|---|---:|
| Hệ số quy đổi thể tích | 6.000 |
| Sức chứa kiện theo khối lượng tính phí | 20.000 g |
| Bậc khối lượng | 500 g |
| Bội số làm tròn tiền | 500 đồng |
| Miền Nam: bậc đầu / mỗi bậc thêm | 22.000 / 4.000 đồng |
| Miền Trung | 30.000 / 5.500 đồng |
| Miền Bắc | 36.000 / 6.500 đồng |
| Phụ phí cồng kềnh | 20.000 đồng |
| Ngưỡng thể tích / cạnh dài | >30.000 cm³ / >100 cm |

Đơn lưu phí, miền và số kiện lúc đặt. Backend tự tính lại, không tin phí client gửi. `expectedShippingFee` là tùy chọn để phát hiện thay đổi, không phải nguồn tính phí.

**Voucher:**

- Một đơn tối đa một freeship và một voucher giảm tiền hàng.
- Freeship giảm đúng phí ship.
- Phần trăm tính trên subtotal, làm tròn xuống đến đồng, không vượt mức giảm tối đa.
- Giảm số tiền không vượt subtotal.
- Đánh dấu dùng khi tạo đơn; hủy đơn trả voucher nhưng **không gia hạn**.

**Cấu hình mặc định:** hạn 30 ngày; freeship và phần trăm yêu cầu subtotal từ 200.000 đồng; phần trăm giảm tối đa 150.000 đồng. Điều kiện được chốt vào voucher khi phát hành; đổi policy áp dụng cho voucher phát hành sau.

**Quà cố định trong code:**

| Sự kiện | Quà |
|---|---|
| Đăng ký | Freeship + 10% + 50.000 đồng |
| Hai nhóm đạt Mới bắt đầu | 10% |
| Hai nhóm đạt Đang phát triển | 20% + freeship |
| Hai nhóm đạt Phong phú | 20% + 100.000 đồng + freeship |

Mốc quà chỉ tính đồ chơi `PURCHASED`, mỗi mốc một lần mỗi trẻ; không dùng điểm từ đồ chơi MANUAL để nhận quà. Hàm phát quà được gọi trong luồng giao đơn cho trẻ thay đổi, không phải mọi lần tính lại hồ sơ.

Căn cứ: `ShippingCalculator.quote/pack/baseFee`, [ShippingProperties.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/shipping/ShippingProperties.java); [VoucherPolicy.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/voucher/VoucherPolicy.java); `VoucherService.grantSkillMilestones/select/discountFor/releaseFor`.

### 4.7. MoMo sandbox và giao dịch lặp

- Endpoint mặc định là MoMo **test-payment**, cấu hình được.
- Backend tạo yêu cầu `captureWallet`, ký HMAC-SHA256.
- Amount là tổng đơn gồm ship và giảm giá, làm tròn VND nguyên `HALF_UP`.
- Mã tham chiếu dạng `<orderId>-<timestamp>`.
- Return chỉ kiểm tra chữ ký/kết quả để hiển thị; **IPN mới cập nhật Payment**.
- Callback thành công phải khớp số tiền đơn.
- Khóa dòng đơn để callback và hủy không cùng xử lý trạng thái cũ.
- Payment đã SUCCESS thì callback lặp trả true; không lặp tác động.
- Có unique Payment theo đơn và unique transaction ID.

**Giới hạn cần ghi rõ:**

- Không lưu đầy đủ lịch sử từng lần tạo URL/thử thanh toán.
- Không thấy đối chiếu callback với một request ID đã lưu; lấy order ID từ tiền tố tham chiếu.
- Không thấy luồng đối soát/refund tự động.
- `PaymentController.momoIpn` bỏ qua boolean trả về của `handleCallback` và vẫn trả `204` khi hàm kết thúc bình thường; callback bị từ chối logic không được phản ánh bằng mã HTTP lỗi.
- `MoMoGateway` dùng HTTP đồng bộ và không khai báo timeout rõ trong mã đã đọc.
- IPN local cần địa chỉ backend mà MoMo truy cập được; URL mặc định localhost tự nó chưa chứng minh callback ngoài mạng hoạt động.

Căn cứ: [MoMoGateway.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/payment/MoMoGateway.java), `buildCreateRequest`, `verifySignature`, `createPaymentUrl`; `PaymentService.handleCallback`; `PaymentController.momoIpn`.

### 4.8. Giới hạn file, thời hạn và tần suất

| Phạm vi | Giới hạn | Cố định/cấu hình |
|---|---|---|
| Giỏ | 1–99 mỗi sản phẩm | Cố định `CartItem.MAX_QUANTITY`. |
| Ảnh sản phẩm | JPG/JPEG/PNG/WebP; 5 MiB/file; tối đa 10 ảnh/sản phẩm | Cố định trong service. |
| Bằng chứng | Ảnh JPG/JPEG/PNG/WebP ≤10 MiB; video MP4/MOV/WebM mặc định ≤100 MiB; tối đa 5 file tình trạng, ngoài video mở hàng | Ảnh/số file cố định; video cấu hình. |
| Multipart toàn backend | 100 MB/file, 200 MB/request trong properties | Cấu hình Spring. Có thể giới hạn thấp hơn setting video. |
| Giữ bằng chứng | Mặc định 30 ngày sau đóng; hold thì giữ | Cấu hình thời gian, cơ chế cố định. |
| Tri thức | PDF/DOCX/TXT/MD ≤10 MiB; TXT/MD UTF-8 | Cố định. |
| Văn bản tri thức | ≤1.000.000 ký tự; ≤1.000 chunk | Cố định. |
| Chia đoạn | Tối đa 1.200 ký tự, overlap 200 | Cố định. |
| Câu hỏi chat | Không rỗng, tối đa 1.000 ký tự | Cố định. |
| Chat rate limit | Mặc định 10 câu/phút/customer, cửa sổ 60 giây | Số câu cấu hình; cửa sổ cố định. |
| Cấu hình chatbot | temperature 0–1; maxTokens 1.024–8.192; topK 1–20; prompt ≤4.000 ký tự | Validation cố định; giá trị dùng được ADMIN chọn. |
| Review/Complaint/phản hồi xử lý | Nội dung tối đa 2.000 ký tự | Cố định DTO. |
| Sản phẩm | Tuổi 0–18; kho 0–1.000.000; gói ≤50.000 g; mỗi cạnh ≤200 cm | Cố định DTO. |
| Phân trang | Nhiều API tối đa 100; thông báo tối đa 50 | Cố định service. |
| Gợi ý tiếp theo | Limit chặn trong 1–20 | Cố định. |
| Gợi ý khi gõ | Tối đa 6 sản phẩm, 3 danh mục, 2 kỹ năng; query chuẩn hóa cắt 100 ký tự | Cố định. |

Frontend có một số giới hạn thời gian/dung lượng viết trực tiếp; thay cấu hình backend không tự cập nhật các nhãn frontend.

---

## 5. Chatbot và RAG theo hành vi thực tế

### 5.1. Dữ liệu đầu vào

`ChatService.prepare` sử dụng:

- Câu hỏi hiện tại.
- Trẻ gắn với session: tên, tuổi và ghi chú.
- `SkillProfile`: điểm, tỷ lệ, nhóm mạnh/yếu.
- Đồ chơi trẻ đã có, gồm dữ liệu sản phẩm và impact.
- Kết quả gợi ý tiếp theo; nhóm quan tâm tác động gián tiếp qua thuật toán này.
- Sản phẩm khớp từ khóa hoặc catalog fallback.
- Tối đa 12 tin nhắn gần đây, mỗi tin bị cắt tối đa 2.000 ký tự, ngân sách văn bản lịch sử 16.000 ký tự.
- Các chunk tri thức truy xuất được.

Không thấy đưa toàn bộ lịch sử đơn hàng hoặc toàn bộ giỏ hàng vào ngữ cảnh mặc định. Lịch sử mua phản ánh chủ yếu qua món `PURCHASED` đã ghi vào trẻ. Khi không chọn trẻ, tuổi chỉ được nhận diện bằng mẫu dạng số + “tuổi”; không thấy bộ phân tích đầy đủ tuổi theo tháng, ngân sách hoặc mọi cách diễn đạt.

### 5.2. Truy xuất sản phẩm

1. Chuẩn hóa câu hỏi, bỏ stop words.
2. Giữ tối đa 12 từ khóa khác nhau có độ dài từ ba ký tự.
3. Tìm sản phẩm active, còn kho và hợp tuổi nếu biết tuổi.
4. Lấy tối đa 60 kết quả sơ bộ rồi xếp theo số từ khóa khớp.
5. Ưu tiên tối đa 10 món khớp, ghép gợi ý theo hồ sơ trẻ, bổ sung món khớp khác.
6. Nếu chưa có ứng viên, lấy catalog hợp tuổi tối đa 12 món và loại món đã có.
7. Ngữ cảnh cuối có tối đa 20 ID được phép đề xuất.

Dữ liệu mỗi món gồm tên, giá, tuổi, danh mục, mô tả cắt 600 ký tự và impact. Đồ chơi đã có gửi tối đa 20 món.

### 5.3. RAG

1. ADMIN tải tài liệu.
2. Worker trích xuất văn bản bằng PDFBox/POI hoặc đọc UTF-8.
3. Tách đoạn, gọi embedding `gemini-embedding-001`, **768 chiều**, theo lô 50 đoạn.
4. Lưu chunk/vector.
5. Khi có câu hỏi và tồn tại tri thức INDEXED, tạo embedding câu hỏi.
6. Đọc chunk theo trang 100, tính cosine bằng Java.
7. Loại vector không hợp lệ và cosine dưới ngưỡng mặc định **0,6**.
8. Giữ topK, gửi nội dung đầy đủ vào ngữ cảnh AI.
9. Lưu nguồn gồm ID tài liệu, tiêu đề và snippet khoảng 300 ký tự để hiển thị.

**Chưa dùng vector index/pgvector trong truy xuất đã đọc.** Mỗi câu hỏi có thể phải duyệt toàn bộ chunk INDEXED, dù đọc theo lô.

### 5.4. Tạo và kiểm soát câu trả lời

`GeminiService.generateAnswer`:

- Có system instruction tiếng Việt, giới hạn vai trò tư vấn và nhắc điểm đồ chơi không phải chẩn đoán.
- Quy định tài liệu/lịch sử/câu hỏi là dữ liệu tham khảo.
- Yêu cầu JSON chứa `content`, `topic`, `products`.
- Đọc cấu hình model, temperature, maxTokens và prompt cửa hàng.
- Giới hạn kết quả sản phẩm còn tối đa 5; nội dung tối đa 16.000 ký tự.
- Có kiểm tra JSON/nội dung và lỗi vượt maxTokens.

`ChatService.saveReply`:

- Chỉ nhận product ID nằm trong whitelist snapshot.
- Loại trùng và kiểm tra lại active/tồn kho.
- Lưu lý do, nguồn, topic và thời gian phản hồi.
- Khóa session, kiểm tra version/tin nhắn cuối trước khi lưu.
- Lưu USER/BOT cùng transaction.

**Ranh giới kiểm soát:** whitelist bảo vệ **thẻ sản phẩm có cấu trúc**. Không thấy bộ hậu kiểm loại mọi tên sản phẩm, giá hoặc khẳng định không có căn cứ trong văn bản `content`. System prompt giảm rủi ro nhưng không chứng minh câu trả lời luôn đúng.

### 5.5. Thiếu dữ liệu hoặc lỗi

| Tình huống | Hành vi |
|---|---|
| Chưa có tri thức INDEXED | Bỏ truy xuất embedding, trả lời bằng ngữ cảnh sản phẩm/hồ sơ nếu AI hoạt động. |
| Không chunk đạt ngưỡng | `sources` rỗng; vẫn gọi AI. |
| Embedding câu hỏi lỗi | Bắt `AiServiceException`, bỏ tri thức, tiếp tục tạo câu trả lời. |
| Không sản phẩm khớp | Fallback catalog; nếu vẫn rỗng thì AI nhận danh sách rỗng và được hướng dẫn hỏi thêm. Không có câu fallback cố định bảo đảm. |
| Thiếu key hoặc cấu hình active | `503`, không lưu cặp tin nhắn. |
| Model lỗi, JSON sai, nội dung sai, hết maxTokens | `503`, không lưu cặp tin nhắn. |
| Context session đổi | `409`, yêu cầu gửi lại. |
| Index lỗi/PDF scan không có text | Về `UPLOADED`; cần sửa file/OCR ngoài hệ thống hoặc reindex. |
| Server dừng giữa index | `KnowledgeIndexingRecovery.resumeInterruptedDocuments` đưa các tài liệu PROCESSING vào worker khi sẵn sàng lại. |

### 5.6. Những giới hạn hiện tại

- Chưa có OCR.
- Chưa có streaming câu trả lời.
- Chưa có đánh giá chất lượng tư vấn bằng tập câu hỏi chuẩn hoặc phản hồi đúng/sai của người dùng.
- Nguồn hiển thị là các đoạn **đã truy xuất**, chưa có kiểm chứng AI thực sự dùng từng đoạn cho từng khẳng định.
- Không có kế hoạch học tập cấu trúc, theo dõi hoạt động trẻ hoặc kiểm chứng tiến bộ thực tế.
- Snapshot xung đột bảo vệ session, không bao trùm mọi thay đổi của hồ sơ trẻ/catalog/config trong lúc chờ AI.
- Bộ đếm hạn mức lưu tại một server; request AI lỗi vẫn đã tiêu thụ lượt vì `acquire` xảy ra trước xử lý.
- Thống kê câu hỏi dựa trên USER đã lưu: câu hỏi thất bại AI không nằm trong tổng số câu hỏi lưu.
- Lịch sử thẻ sản phẩm được dựng từ dữ liệu catalog hiện tại; chưa phải snapshot bất biến của giá/mô tả lúc tư vấn.

Căn cứ toàn bộ phần này: `ChatService.prepare/matchProducts/retrieve/saveReply`, `KnowledgeBaseService.retrieve/cosine`, [KnowledgeDocumentStorage.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/KnowledgeDocumentStorage.java), `readText/splitText`; [GeminiService.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/GeminiService.java), `generateAnswer/embedQuery/embedDocuments`; [KnowledgeIndexingRecovery.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/ai/KnowledgeIndexingRecovery.java), `resumeInterruptedDocuments`.

---

## 6. Yêu cầu phi chức năng và môi trường vận hành

### 6.1. Cơ chế đã triển khai

| Nhóm | Bằng chứng trong mã | Giới hạn của kết luận |
|---|---|---|
| Xác thực | JWT Bearer, kiểm chữ ký/hạn/email/`tokenVersion`; tải trạng thái enabled từ DB. | Chưa kiểm thử xâm nhập; chưa có refresh token hoặc thu hồi theo từng phiên. |
| Mật khẩu | BCrypt qua `ApplicationConfig.passwordEncoder`. | JWT lưu localStorage; cần cân nhắc rủi ro XSS khi vận hành. |
| Phân quyền | `/api/admin/**` và `@PreAuthorize`; frontend route guard. | Frontend chỉ hỗ trợ điều hướng; không phải biện pháp bảo vệ dữ liệu duy nhất. |
| Quyền sở hữu | Guard trẻ/đơn; chat lọc customer; giỏ theo customer; voucher và bằng chứng kiểm chủ sở hữu. | Không suy ra đã kiểm tra toàn bộ endpoint bằng kiểm thử bảo mật thực tế. |
| Che giấu dữ liệu | Nhiều truy cập ID người khác trả `404`; DTO không trả mật khẩu/key. | ADMIN được xem hội thoại khách theo thiết kế. |
| File | Giới hạn loại/dung lượng, kiểm chữ ký ảnh/video; UUID và kiểm đường dẫn; tài liệu tri thức tách public uploads. | Không thấy quét malware; bằng chứng cần giữ cấu hình thư mục riêng đúng khi triển khai. |
| API | JSON envelope `success/message/data`, handler lỗi thống nhất; Axios gửi Bearer và xử lý `401`. | MoMo return cần đọc cờ success trong envelope; không phải mọi HTTP 200 là nghiệp vụ thành công. |
| CORS | Danh sách origin cấu hình cho API; stateless, CSRF tắt. | Không thấy cấu hình HTTPS/reverse proxy trong các file đã đối chiếu. |
| Responsive | Tailwind `sm/md/lg`, layout và modal; Recharts cho radar/timeline. | Có mã responsive, chưa có kiểm chứng trực quan trên máy tính bảng/điện thoại. |
| Hạn mức dịch vụ | Chat rate limiter, SMTP cooldown, giới hạn upload. | Không thấy rate limit chung cho login/API public; lượt xem có thể bị tăng bằng request lặp. |
| Tối ưu truy vấn | Phân trang, batch ảnh/impact/rating, aggregate thống kê; lazy load trang frontend. | Chưa có số đo latency/tải đồng thời. |

Căn cứ: [JwtAuthenticationFilter.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/security/JwtAuthenticationFilter.java), `doFilterInternal`; [ChildProfileAccessGuard.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/child/ChildProfileAccessGuard.java), `getOwnedChildProfile`; [OrderAccessGuard.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/service/order/OrderAccessGuard.java), `getOwnedOrder`; [client.ts](D:/Nien_Luan/frontend/src/api/client.ts); [GlobalExceptionHandler.java](D:/Nien_Luan/backend/src/main/java/com/brainblocks/backend/exception/GlobalExceptionHandler.java); `ProductImageService`, `ComplaintEvidenceStorage`, `KnowledgeDocumentStorage`.

### 6.2. Nhất quán và an toàn dữ liệu

**Có cơ chế trong mã:**

- Transaction cho đăng ký, đặt/hủy đơn, xử lý phản hồi và cập nhật hồ sơ.
- Khóa giỏ khi checkout để tránh hai đơn dùng cùng giỏ.
- Trừ kho có điều kiện bằng UPDATE nguyên tử.
- Khóa đơn cho chuyển trạng thái/hủy/IPN.
- Khóa tài khoản khi đổi/đặt lại mật khẩu.
- Unique cho giỏ–sản phẩm, trẻ–sản phẩm, customer–product review, Payment–order và transaction ID.
- Khóa chung khi kích hoạt chatbot.
- AI/index chạy mạng ngoài transaction; lưu kết quả bằng transaction ngắn.
- Callback dọn file upload khi rollback, dọn file cũ sau commit trong kho tri thức.
- Thông báo tạo trong transaction nghiệp vụ để rollback cùng thao tác.

**Không nên viết “toàn bộ hệ thống đã chống mọi xung đột đồng thời”:**

- `CartService.addItem/updateItemQuantity` không dùng cùng khóa giỏ như checkout.
- `AdminProductService.updateStock` gán số tuyệt đối; chưa thấy khóa/`@Version` trên Product để phối hợp đầy đủ với thao tác kho khác.
- `AdminComplaintService.updateStatus` không có khóa dòng tương tự đơn; cần kiểm tra xử lý đồng thời với job.
- `ComplaintEvidenceStorage.delete` nuốt lỗi xóa và ghi log; caller vẫn có thể đánh dấu `purgedAt`. Chưa bảo đảm mọi file đánh dấu dọn đã bị xóa vật lý thành công.
- Script SQL có constraint/index bổ sung, nhưng sự tồn tại của script không chứng minh đã áp dụng vào DB local.

Căn cứ: các service nêu trên; [schema-v5-constraints.sql](D:/Nien_Luan/backend/docs/sql/schema-v5-constraints.sql), gồm unique email không phân biệt hoa/thường và unique cấu hình active.

### 6.3. Công nghệ và điều kiện local

| Thành phần | Theo khai báo dự án |
|---|---|
| Backend | Java 21; Spring Boot **4.1.1**; Spring Security, Validation, JPA/Hibernate; Maven wrapper. |
| Database vận hành | PostgreSQL. |
| Database kiểm thử | H2 trong profile test. |
| Frontend | React 19, TypeScript, Vite 8; Tailwind CSS 4; React Router, TanStack Query, Axios, React Hook Form/Zod, Recharts. |
| AI | Google GenAI Java SDK; Gemini tạo câu trả lời và embedding. |
| Trích xuất tài liệu | PDFBox, Apache POI. |
| File | Lưu filesystem local: ảnh public, bằng chứng và tri thức ở các thư mục riêng. |
| Dev API | Vite mặc định cổng 5173; proxy `/api` và `/uploads` tới backend mặc định 8080. |

Điều kiện chạy local:

1. JDK phù hợp Java 21, Node.js/npm phù hợp dependency Vite đang khóa, Maven wrapper hoạt động.
2. PostgreSQL và cấu hình datasource hợp lệ.
3. Cấu hình JWT hợp lệ; thông tin ADMIN khởi tạo và các cấu hình bắt buộc được cấp qua môi trường/properties.
4. SMTP hợp lệ để người dùng thật nhận mã đăng ký và link reset.
5. Google API key phía server và tài khoản/quota phù hợp để chat/index hoạt động.
6. MoMo sandbox và URL callback truy cập được từ MoMo nếu kiểm chứng IPN.
7. Backend có quyền ghi các thư mục file; tài nguyên địa chỉ JSON có trong classpath.

Lệnh theo scripts/wrapper:

```powershell
# Tại backend
.\mvnw.cmd spring-boot:run

# Tại frontend
npm install
npm run dev
```

**Chưa xác nhận Node version cụ thể được quy định thống nhất trong repository.** Không kiểm tra phiên bản công cụ cài trên máy hoặc cấu hình bí mật thực tế.

Căn cứ: [pom.xml](D:/Nien_Luan/backend/pom.xml), [package.json](D:/Nien_Luan/frontend/package.json), [vite.config.ts](D:/Nien_Luan/frontend/vite.config.ts), [application.properties](D:/Nien_Luan/backend/src/main/resources/application.properties).

### 6.4. Mục tiêu tài liệu và kiểm thử

- **Dưới 3 giây** là mục tiêu trong đề bài. `App.tsx` có lazy loading và mã có tối ưu truy vấn, nhưng **chưa có số đo chứng minh đạt mục tiêu**.
- Responsive có mã triển khai, chưa chứng minh bằng kiểm thử thiết bị.
- Test HTTP backend dùng server test và H2 có thể kiểm tra nhiều lớp ứng dụng, nhưng không thay thế kiểm thử PostgreSQL và dịch vụ ngoài.
- `ChatbotApiTest` mock `GeminiService`; test mail có mock; `MoMoPaymentAmountTest` mock gateway trong kiểm tra callback.
- Không thấy script test frontend trong `package.json`; build/lint không tương đương kiểm thử hành vi người dùng.

---

## 7. Đối chiếu với đề bài giảng viên

Trong cột mức độ: **Mã** = có triển khai; **Test** = đã đọc thấy kiểm tra tự động liên quan, **chưa chạy**; **Thực tế** = chưa xác nhận tích hợp thực tế trong lần phân tích này.

| Yêu cầu | Mã nguồn liên quan | Mức độ triển khai | Điểm thiếu hoặc cần xác nhận |
|---|---|---|---|
| **2.1 — Đăng ký, đăng nhập, đăng xuất, cập nhật cá nhân** | `AuthService.register/login`, `UserService`, `AuthContext.logout` | **Mã:** có. **Test:** registration/reset/đổi mật khẩu có assertion. **Thực tế:** chưa xác nhận. | Đăng xuất chỉ phía client; SMTP thật chưa kiểm chứng. |
| **2.1 — Hồ sơ trẻ, tuổi, kỹ năng quan tâm và lịch sử mua** | `ChildProfileService`, `OrderService.getMyOrders`, `ChildProduct` | **Mã:** có. **Test:** có kịch bản trẻ/đơn/giao hàng. **Thực tế:** chưa xác nhận. | “Sản phẩm đã lựa chọn” cần định nghĩa: giỏ hàng không tự vào hồ sơ; MANUAL hoặc mua đã giao mới là đồ chơi hiện có. |
| **2.2 — Tìm kiếm/lọc/sắp xếp/chi tiết** | `ProductService`, `ProductSpecifications`, `ProductMatchService`, `ProductListPage` | **Mã:** có. **Test:** assertion tìm bỏ dấu, gợi ý, match và lọc. **Thực tế:** chưa xác nhận. | Phân biệt relevance công khai với fit cá nhân hóa; nhiều skill là OR theo ngưỡng. |
| **2.3 — Giải thích lợi ích, tư vấn theo tuổi/kỹ năng** | `ChatService.prepare/matchProducts`, `GeminiService.generateAnswer` | **Mã:** có. **Test:** API/service với Gemini mock. **Thực tế:** chưa xác nhận. | Chưa có đánh giá chất lượng ngôn ngữ và tính đúng với Gemini thật. |
| **2.3 — Phân tích đồ chơi đã có, gợi ý tiếp theo và giải thích** | `ChildProduct`, `SkillRecommendationService`, `ChatService` | **Mã:** có. **Test:** lọc ID bịa, context trẻ, lý do/nguồn được lưu. **Thực tế:** chưa xác nhận. | Không dùng toàn bộ lịch sử đơn/giỏ; whitelist chưa hậu kiểm văn bản tự do. |
| **2.4 — Hồ sơ/biểu đồ/phân bố kỹ năng** | `SkillScoreCalculator`, `SkillProfileService`, `SkillRadar` | **Mã:** có. **Test:** calculator và các kịch bản cập nhật hồ sơ. **Thực tế:** chưa xác nhận UI. | Điểm phản ánh đồ chơi, không năng lực trẻ; số lượng cùng sản phẩm không nhân điểm. |
| **2.4 — Lộ trình phát triển và nhóm ít được khai thác** | `getTimeline`, `SkillTimelineChart`, `getWeakestSkill`, `recommendNext` | **Mã:** có ở dạng biểu đồ/gợi ý. **Test:** có kiểm tra một số hành vi gợi ý. **Thực tế:** chưa xác nhận. | Chưa có kế hoạch giáo dục hoặc snapshot lịch sử bất biến. |
| **2.5 — Giỏ, địa chỉ, đặt đơn, theo dõi** | `CartService`, `OrderService`, `CheckoutPage`, `LocationDirectory` | **Mã:** có. **Test:** checkout đồng thời, địa chỉ, ship và snapshot đơn. **Thực tế:** chưa xác nhận. | Dữ liệu địa chỉ theo 63 tỉnh trước sáp nhập; đồng thời chỉnh giỏ/checkout cần kiểm chứng thêm. |
| **2.6 — Đánh giá, nhận xét và phản hồi** | `ReviewService`, `ComplaintService`, `ComplaintModal` | **Mã:** có. **Test:** điều kiện đánh giá/bằng chứng/quyền và trạng thái. **Thực tế:** chưa xác nhận. | Chưa có sửa/xóa Review phía khách; nghiệp vụ trả hàng còn giới hạn. |
| **2.7 — Danh sách, trạng thái, khóa/mở và quản lý quyền** | `AdminUserService`, `SecurityConfig`, `User.getRole` | **Mã:** danh sách/khóa/phân quyền có. **Test:** một số assertion role/token. **Thực tế:** chưa xác nhận. | **Chưa có API/giao diện đổi vai trò hoặc quản lý permission tùy chỉnh.** |
| **2.8 — CRUD sản phẩm, ảnh, kho, tuổi, danh mục, impact** | `AdminProductService`, `ProductImageService`, `ProductRequest` | **Mã:** có. **Test:** validation gói, video, ảnh và catalog. **Thực tế:** chưa xác nhận. | “Xóa” là ẩn; cần xác nhận chấp nhận phạm vi này. Kho chưa có sổ nhập/xuất riêng. |
| **2.9 — Danh mục, kỹ năng và cập nhật trọng số/chỉ số** | `CategoryService`, `SkillService`, `applyImpacts`, `SkillScoreCalculator` | **Mã:** CRUD và impact có. **Test:** guard xóa/code. **Thực tế:** chưa xác nhận. | Không thấy trọng số riêng theo từng skill cho ADMIN chỉnh; alpha là cấu hình toàn hệ thống, trọng số nhóm yếu = 2 viết trong code. |
| **2.10 — Quản lý đơn, hủy/đổi/trả, khiếu nại** | `AdminOrderService`, `AdminComplaintService`, `ReturnPolicy` | **Mã:** workflow có. **Test:** một số chuyển trạng thái/return deadline. **Thực tế:** chưa xác nhận. | Chưa tạo đơn đổi, theo dõi vận đơn trả, hoàn tiền tự động hoặc nhập lại kho sau trả. |
| **2.11 — Quản lý đánh giá và nội dung không phù hợp** | `ReviewService.updateVisibility`, trang admin Reviews | **Mã:** ẩn/hiện có. **Test:** assertion visibility. **Thực tế:** chưa xác nhận. | Chưa có tự động kiểm duyệt nội dung; xử lý bằng ADMIN. |
| **2.12 — Upload, cập nhật, xóa, trạng thái và tri thức** | `KnowledgeBaseService`, storage/worker/recovery, trang Knowledge | **Mã:** có. **Test:** lifecycle/file/chunk với mock embedding. **Thực tế:** chưa xác nhận Gemini index thật. | Không OCR; retrieval quét cosine bằng Java; trạng thái lỗi dùng UPLOADED. |
| **2.13 — Giám sát hội thoại/câu hỏi/cấu hình** | `ChatService.getSessionsForAdmin/getStats`, `ChatbotConfigService` | **Mã:** có. **Test:** API giám sát và cấu hình với mock. **Thực tế:** chưa xác nhận. | Chưa có đánh giá/duyệt/sửa câu trả lời theo quy trình riêng. |
| **2.14 — Thống kê người dùng, sản phẩm, đơn, quan tâm/mua, skill, chat** | `AdminStatsService`, `ChatService.getStats`, repositories aggregate | **Mã:** có. **Test:** một số assertion dashboard/ship/chat. **Thực tế:** chưa xác nhận số liệu DB thực. | Cần chuẩn hóa định nghĩa “đã bán”, “doanh thu”, “quan tâm”; chưa thấy xuất báo cáo. |
| **C — Hai nguồn sản phẩm và RAG; không huấn luyện mô hình riêng** | `ChatService`, `KnowledgeBaseService`, `GeminiService` | **Mã:** có. **Test:** kiểm tra lọc/nguồn/fallback bằng mock. **Thực tế:** chưa xác nhận. | Chưa chứng minh chất lượng truy xuất hoặc độ đúng tư vấn bằng tập dữ liệu đánh giá. |
| **Hiệu năng — Trang chính <3 giây** | Lazy routes trong `App`, phân trang/batch query | **Mã:** có biện pháp. **Test đo hiệu năng:** chưa xác định. **Thực tế:** chưa đo. | Không được kết luận đã đạt <3 giây. |
| **Giao diện responsive, biểu đồ trực quan** | Tailwind layout, `SkillRadar`, `SkillTimelineChart` | **Mã:** có. **Test UI/thiết bị:** chưa xác định. **Thực tế:** chưa kiểm tra trực quan. | Cần kiểm thử các kích thước màn hình và thao tác thực tế. |
| **JWT, phân quyền, key AI server, bảo vệ dữ liệu** | Security/filter/guards, `GeminiConfig`, DTO/API client | **Mã:** có. **Test:** một số quyền/ownership/token. **Thực tế:** chưa security test tổng thể. | Cần kiểm chứng cấu hình triển khai, logging và phạm vi quyền toàn API. |
| **Tách frontend–backend–DB, mở rộng dữ liệu/AI** | React/API, Spring/JPA, catalog/knowledge/config | **Mã:** có kiến trúc tách và dữ liệu mở rộng. **Test:** theo từng nghiệp vụ. **Thực tế:** chưa xác nhận mở rộng tải. | Model tên cấu hình được, nhưng provider hiện gắn Gemini; thay provider cần phát triển mã. |

### Bằng chứng kiểm thử cụ thể đã đọc

| File test | Assertion/kịch bản liên quan | Phạm vi bằng chứng |
|---|---|---|
| [RegistrationEmailCodeTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/RegistrationEmailCodeTest.java) | `registrationNeedsTheCodeSentToTheEmail`, `concurrentGuessesCannotExceedTheAttemptLimit` | Kiểm tra mã và giới hạn thử; mail giả lập. |
| [PasswordResetTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/PasswordResetTest.java) | `resetLinkSetsNewPasswordOnceAndLogsOutOldSessions`, `sameLinkUsedConcurrentlySucceedsOnce` | Kiểm tra token dùng một lần/vô hiệu phiên; mail giả lập. |
| [ReviewFindingsRegressionTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/ReviewFindingsRegressionTest.java) | `concurrentCheckoutOfSameCartCreatesOneOrder`, `unpaidMomoOrderIsCancelledAfterDeadline`, `orderItemShowsChildAndDeliveryRecalculatesOncePerChild` | Assertion nghiệp vụ qua môi trường test, chưa xác nhận PostgreSQL thực. |
| [CatalogAndFeedbackTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/CatalogAndFeedbackTest.java) | `productMatchesFollowChildAndBundlePreviewSkipsOwned`, `evidenceIsRequiredAndPrivate`, `approvedReturnExpiresAfterSevenDays` | Kiểm tra kết quả match/gain, bằng chứng/quyền và deadline. |
| [LocationAndShippingTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/LocationAndShippingTest.java) | `orderTotalIsSubtotalPlusServerComputedShipping`, `mismatchedExpectedShippingFeeReturns409` | Assertion tính tiền/rollback do phí thay đổi. |
| [VoucherTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/VoucherTest.java) | `vouchersApplyToOrderAndComeBackWhenCancelled`, `cannotUseAnotherCustomersVoucher`, `skillMilestonesGrantVouchersOncePerChild` | Kiểm tra áp dụng/quyền/phát quà. |
| [MoMoPaymentAmountTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/service/payment/MoMoPaymentAmountTest.java) | `callbackWithWrongAmountIsNotRecordedAsPaid`, `callbackAfterCancelKeepsOrderCancelledAndAsksForRefund` | Gateway mock; không gọi MoMo thật. |
| [ChatbotApiTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/regression/ChatbotApiTest.java) | `customerChatsAndAdminMonitorsConversation`, `adminConfiguresChatbotAndManagesKnowledgeDocuments` | HTTP test với Gemini mock. |
| [ChatbotServicesTest.java](D:/Nien_Luan/backend/src/test/java/com/brainblocks/backend/service/ai/ChatbotServicesTest.java) | `replyFiltersInventedProductIdsAndPreservesReasonsAndSourcesInHistory`, `failedAiCallDoesNotLeaveHalfOfAConversation`, `changeDuringAiCallRejectsStaleAnswer` | Kiểm tra logic service; không chứng minh chất lượng AI thật. |

Các dòng trên mô tả **test kiểm tra điều gì**, không phải báo cáo test đã pass.

---

## 8. Mâu thuẫn và câu hỏi cần nhóm xác nhận

| Điểm cần xác nhận | Đối chiếu thực tế | Câu hỏi cho nhóm |
|---|---|---|
| Chính sách mật khẩu | `frontend/src/lib/password.ts` buộc chữ+số; DTO backend chỉ buộc dài 8–72. | Quy tắc nghiệp vụ chính thức là dài tối thiểu hay bắt buộc chữ+số? |
| Giá trị thời hạn trên UI | Register ghi 10 phút/60 giây; Reset ghi 30 phút; Policies ghi MoMo 24 giờ, trong khi backend cấu hình được. | Giữ cấu hình mặc định cố định cho báo cáo hay cung cấp policy từ API? |
| “Khách dùng chatbot” | Trang chat công khai nhưng gửi câu hỏi cần CUSTOMER. | Chương 1 có mô tả khách chưa đăng nhập được tư vấn không? Nếu có, hiện code chưa đáp ứng. |
| “Lựa chọn/mua” ảnh hưởng hồ sơ | Giỏ/chọn checkout/thanh toán chưa ghi đồ chơi; MANUAL hoặc DELIVERED mới ghi. | Nhóm định nghĩa “đã lựa chọn” là thêm MANUAL hay cả giỏ hàng? |
| “Lộ trình phát triển” | Timeline dựng từ đồ chơi hiện còn và impact hiện tại. | Chấp nhận biểu đồ lịch sử đồ chơi hay cần kế hoạch theo giai đoạn? |
| Quản lý quyền người dùng | Có role cố định theo subclass và khóa/mở khách; không có đổi role. | “Quản lý quyền” trong đề được coi là phân quyền hệ thống hay thao tác ADMIN thay role? |
| Đổi/trả và hoàn tiền | Có Complaint workflow; thiếu đơn đổi/refund/nhập kho trả tự động. | Nhóm sẽ mô tả phạm vi là tiếp nhận và theo dõi yêu cầu, hay còn phải hoàn thiện nghiệp vụ? |
| Đơn MOMO chưa trả | ADMIN có thể xác nhận/giao khi chưa thấy Payment SUCCESS; timeout chỉ xét PENDING. | Có cho ADMIN xác nhận thủ công đơn chưa trả không? Khi đó cơ chế quá hạn nào áp dụng? |
| Giao dịch MoMo sau hủy | Payment SUCCESS nhưng order vẫn CANCELLED; UI có thể hiện “đã xác nhận đơn” khi chỉ thấy SUCCESS. | Cần phân biệt “đã nhận tiền” và “đơn được thực hiện/đang chờ hoàn tiền” trong báo cáo và UI? |
| Định nghĩa “đã bán” | `sumSoldQuantity/findTopSelling` tính mọi đơn trừ CANCELLED, gồm PENDING; tổng tiền hàng dashboard chỉ DELIVERED. | “Đã bán” có phải chỉ DELIVERED? Có trừ lượng trả không? |
| Định nghĩa doanh thu | Tổng tiền hàng là subtotal; chưa giảm voucher/hoàn trả. Biểu đồ ngày loại CANCELLED nhưng không chỉ DELIVERED. | Báo cáo nên gọi là giá trị đơn, tiền hàng giao thành công hay doanh thu thuần? |
| Địa chỉ giao hàng | `LocationDirectory` dùng 63 tỉnh trước sáp nhập 07/2025. | Đây là dữ liệu demo được chấp nhận hay cần thay dữ liệu vận hành hiện tại? |
| Trả hàng nhiều lần | Chặn từng request vượt dòng mua, chưa thấy chặn tổng nhiều request. | Có cho tạo nhiều yêu cầu cùng dòng và quy tắc lượng trả còn lại là gì? |
| Quá hạn trả hàng | Có lời chú thích “admin vẫn từ chối tay được”, nhưng map thông thường không cho PROCESSING→REJECTED; job mới làm được. | Đâu là chuyển trạng thái mong muốn? |
| Đối tượng tạo tài khoản | Policies nói trẻ không tự tạo tài khoản, nhưng đăng ký không xác minh tuổi/vai trò phụ huynh. | Đây là mô tả đối tượng sử dụng hay điều kiện hệ thống phải kiểm tra? |
| Nguồn RAG | Tài liệu mô tả sources là căn cứ; code lưu các đoạn truy xuất, chưa kiểm tra từng khẳng định dùng nguồn. | Báo cáo nên dùng “nguồn tham khảo truy xuất” thay cho “nguồn đã được chứng minh sử dụng”? |
| Tích hợp thật và hiệu năng | Có mã/test mock; chưa có kết quả chạy, benchmark hoặc biên bản tích hợp được kiểm chứng ở đây. | Nhóm có log test, video luồng SMTP/Gemini/MoMo và số đo tải trang để bổ sung bằng chứng không? |

**Cách diễn đạt phù hợp trong báo cáo:** dùng “hệ thống có triển khai cơ chế…” cho bằng chứng mã; dùng “có kiểm thử tự động kiểm tra…” cho test đã đọc; chỉ dùng “đã hoạt động thành công”, “đạt dưới 3 giây” hoặc “đã tích hợp thực tế” khi nhóm có kết quả thực nghiệm tương ứng.

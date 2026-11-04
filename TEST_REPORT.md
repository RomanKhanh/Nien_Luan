# BÁO CÁO KIỂM THỬ TOÀN DIỆN BRAINBLOCKS

- Branch: `test/full-system-testing`. Ngày chạy: 04/11/2026.
- Phạm vi: backend Spring Boot (JUnit 5, Mockito, MockMvc) và frontend React (Vitest, Testing Library, jsdom).
- Không sửa code chính (`backend/src/main`, `frontend/src`). Không commit, không push.
- Database: mọi test backend chạy trên **H2 trong bộ nhớ** (profile `test`, `create-drop`), đã kiểm tra trong log surefire: chỉ có `jdbc:h2`. Không test nào kết nối Postgres `brainblocks`.
- AI: không gọi Gemini thật. Phần chatbot dùng bản giả lập/mocks.

## 1. Tổng hợp kết quả

| Cụm | Người viết | Số test | Pass | Fail | Ghi chú |
|---|---|---:|---:|---:|---|
| A. Người dùng & Hồ sơ trẻ (backend) | Codex | 41 | 41 | 0 | |
| B. Sản phẩm & Kỹ năng (backend) | Codex | 102 | 100 | 2 | 2 fail = bug thật |
| C. Giỏ hàng, Đơn hàng, Thanh toán (backend) | Codex | 90 | 90 | 0 | |
| D. Chatbot AI & RAG (backend) | Codex | 37 | 36 | 1 | 1 fail = bug thật |
| Frontend (đăng nhập, sản phẩm, giỏ hàng, hồ sơ bé, nhiều tab) | Claude | 53 | 50 | 3 | 3 fail = bug thật |
| Test backend có sẵn từ trước (regression, auth, AI...) | nhóm dự án | 199 | 193 | 6 | 6 lỗi = test cũ lỗi thời, không phải bug (xem mục 2) |
| **Tổng** | | **522** | **510** | **12** | backend 469 (460 pass), frontend 53 (50 pass) |

Trong các test mới viết, mọi test đang fail (2 + 1 + 3) đều **cố ý giữ lại vì chúng phát hiện bug thật** trong code chính (xem mục 2). Không còn test mới nào fail vì viết sai. 6 lỗi còn lại thuộc một test có sẵn từ trước, đã lỗi thời (xem cuối mục 2).

Lệnh chạy toàn bộ: `cd backend && ./mvnw test` cho kết quả `Tests run: 469, Failures: 3, Errors: 6`. Test pgvector bị loại theo cấu hình mặc định của `pom.xml`.

### Chi tiết frontend

| File | Luồng | Test | Pass |
|---|---|---:|---:|
| `api-client.test.ts` | JWT gắn vào request, 401 tự đăng xuất, dịch lỗi backend | 9 | 9 |
| `auth.test.tsx` | Đăng nhập (validate, đúng/sai, bị khóa, admin), chặn route theo vai trò | 9 | 9 |
| `products.test.tsx` | Danh sách, từ khóa, lọc tuổi/kỹ năng/giá/danh mục, sắp xếp, phân trang, thêm vào giỏ | 13 | 13 |
| `cart.test.tsx` | Xem giỏ, đổi số lượng, xóa món, xóa hết, vượt tồn kho, ngừng bán | 11 | 11 |
| `children.test.tsx` | Danh sách, tạo/sửa/xóa hồ sơ bé, validate, lỗi 404 | 9 | 8 |
| `multi-tab.test.tsx` | Đăng xuất / đổi tài khoản ở tab khác | 2 | 0 |

### Lệnh chạy lại

```bash
# backend (H2, không cần Postgres)
cd backend && ./mvnw test -Dtest='com.brainblocks.backend.fulltest.**'
# frontend
cd frontend && npm test
```

## 2. Bug thật phát hiện được

| # | Mức độ | Cụm | Mô tả | Vị trí | Test phát hiện |
|---|---|---|---|---|---|
| 1 | Trung bình | A. Người dùng & Hồ sơ trẻ (frontend) | Form hồ sơ bé tính "hôm nay" theo giờ **UTC** (`toISOString`). Ở VN (UTC+7) trước 7h sáng, form từ chối nhầm bé sinh hôm qua, và thuộc tính `max` của ô ngày cũng lệch 1 ngày. | `frontend/src/features/children/ChildFormModal.tsx:16` (dùng ở dòng 24, 119) | `children.test.tsx` › "ngày sinh hôm qua theo giờ địa phương…" |
| 2 | Nhẹ | B. Sản phẩm & Kỹ năng (ảnh hưởng nhiều cụm) | `GET /api/products?page=2147483647` trả **500** (`Page offset exceeds Integer.MAX_VALUE`) thay vì 400 hoặc trang rỗng. Lỗi `InvalidDataAccessApiUsageException` không được `GlobalExceptionHandler` xử lý. Antigravity chỉ ra và Claude đã kiểm tra: các API phân trang khác cũng không giới hạn `page`, gồm `AdminOrderService:60`, `AdminUserService:33`, `ReviewService:45,87`, `NotificationService:53`, và admin products (gọi lại `ProductService`). | `backend/.../service/product/ProductService.java:59` | `CatalogApiTest.oversizedSizeIsCappedAndLargePageIsEmpty` |
| 3 | Nhẹ | B. Sản phẩm & Kỹ năng | Lọc `ageFrom > ageTo` không bị chặn (trả 200), trong khi `minPrice > maxPrice` bị chặn (400). Hai bộ lọc xử lý không nhất quán. | `backend/.../service/product/ProductService.java:53-56` | `CatalogApiTest.reversedAgeRangeIsBadRequest` |
| 4 | Trung bình | A. Người dùng & Hồ sơ trẻ | Ghi đè khi cập nhật đồng thời: `updateMyProfile` và `updateStatus` không khóa dòng, `User` không có `@Version`. Nếu admin khóa tài khoản đúng lúc khách lưu hồ sơ, tài khoản có thể bị mở lại; nếu khách đổi mật khẩu đúng lúc đó, mật khẩu cũ có thể bị khôi phục. | `backend/.../service/user/UserService.java:34`, `AdminUserService.java:50`, `entity/User.java:24` | Phát hiện qua review code (Codex), chưa có test tự động vì cần điều phối 2 transaction. |
| 5 | Nhẹ | A. Người dùng & Hồ sơ trẻ | `birthDate` dùng `@Past` nên không tạo được hồ sơ cho bé sinh **hôm nay**; nên dùng `@PastOrPresent`. | `backend/.../dto/request/child_profile/ChildProfileRequest.java:15` | Phát hiện qua review code. |
| 6 | Trung bình | D. Chatbot AI & RAG | Response của `POST` gửi tin nhắn chat trả `userMessage` và `botMessage` với **`id = null`, `createdAt = null`** (lịch sử chat thì có đủ). Nguyên nhân: `sessions.saveAndFlush(session)` chạy `merge`, cascade tạo bản sao được quản lý cho tin nhắn mới, còn response lại dựng từ đối tượng gốc chưa lưu. Frontend dùng đúng các trường này: `ChatPanel.tsx:123` (`setTypingId(reply.botMessage.id)`, hiệu ứng gõ chữ), `:248` (`key={m.id}`), `:131` (`lastMessageAt`), `MessageBubble.tsx:149` (giờ gửi hiện "—"). | `backend/.../service/ai/ChatService.java:325-327` | `ChatbotFullApiTest.recommendationsRespectStockAgeAndOwnershipAndPromptContainsActualSkills` |
| 7 | Trung bình | A. Người dùng & Hồ sơ trẻ (frontend + backend) | **Đăng xuất không đồng bộ giữa các tab.** Mở 2 tab cùng tài khoản, tab 1 đăng xuất thì tab 2 vẫn dùng bình thường cho tới khi refresh. Nếu tab 1 đăng nhập tài khoản khác, tab 2 vẫn gửi token của tài khoản cũ. Nguyên nhân: `tokenStorage` chỉ đọc `localStorage` một lần lúc tải trang rồi giữ trong biến bộ nhớ, và không nghe sự kiện `storage` (cả app chỉ `RecentlyViewed.tsx:41` có nghe). Lỗi bị nặng thêm vì backend không thu hồi token khi đăng xuất (chức năng thiếu số 3 ở mục 3), nên token cũ vẫn dùng được tới khi hết hạn. Rủi ro: máy dùng chung, người sau vẫn xem được hồ sơ bé, đơn hàng, địa chỉ ở tab còn mở. | `frontend/src/auth/tokenStorage.ts:7`, `frontend/src/auth/AuthContext.tsx` (không có listener `storage`) | `multi-tab.test.tsx` (2 test) |

`backend/...` = `backend/src/main/java/com/brainblocks/backend`.

**Xác minh chéo:** Antigravity được giao đọc code độc lập và kết luận cả 5 bug từ 1 đến 5 đều **ĐÚNG**. Hai bên chỉ khác nhau ở mức độ: Antigravity đánh bug 4 là "nghiêm trọng" và bug 1 là "nhẹ". Claude giữ cả hai ở mức trung bình. Bug 4 cần hai request chồng nhau trong vài mili giây mới xảy ra. Bug 1 thì xảy ra mỗi ngày trong khung 0h–7h sáng với bé sinh hôm trước. Antigravity cũng lưu ý thêm: nếu server chạy theo múi giờ UTC thì `@Past` ở bug 5 cũng lệch tương tự.

### Test có sẵn đang lỗi (không phải bug code chính)

- `service/ai/ProductEmbeddingServiceTest`: **6 lỗi**, cả 6 đều do `NoSuchBeanDefinitionException: LowStockNotifier`. Sau commit low-stock (`7907628`), `AdminProductService` cần thêm `LowStockNotifier` và `LowStockPolicy`, nhưng `@Import` của test chưa được cập nhật. Lỗi này có sẵn trên `master` và không liên quan đến test mới. Lúc lập báo cáo ban đầu, theo luật không sửa test có sẵn nên chưa áp dụng. **Đã sửa ở mục 6** (thêm `LowStockPolicy` vào `@Import` và mock `LowStockNotifier`).

### Test viết sai đã sửa (không phải bug)

- `CatalogApiTest.duplicateSkillNameIsRejected`: test giả định tên nhóm kỹ năng phải duy nhất. Thực tế theo thiết kế chỉ **mã** kỹ năng là duy nhất (`entity/Skill.java:27`), đề bài cũng không yêu cầu tên duy nhất. Đã giao lại Codex sửa thành `duplicateSkillNameWithDifferentCodeIsAllowed`, và test này pass.
- 1 test frontend fail do jsdom thiếu `scrollIntoView` (lỗi môi trường): đã bổ sung hàm giả trong `frontend/tests/setup.ts`.

## 3. Chức năng trong đề bài chưa có code hoặc chưa test được

**Chưa có code** (Antigravity đối chiếu đề bài với code, Claude đã kiểm tra lại các class được nêu):

1. **2.7 Quản lý quyền truy cập:** admin chỉ khóa/mở khóa được khách hàng, chưa có API đổi vai trò hay cấp/thu hồi quyền.
2. **2.9 Cập nhật trọng số kỹ năng:** hệ số trong `SkillScoreCalculator` đọc tĩnh từ cấu hình server, admin chưa chỉnh được qua API/UI. Chỉ sửa được chỉ số trên từng sản phẩm.
3. **2.1 Đăng xuất phía server:** không có `/api/auth/logout`. Đăng xuất chỉ xóa token ở trình duyệt, token vẫn dùng được tới khi hết hạn (trừ khi đổi mật khẩu).
4. **2.13 Chọn sản phẩm dùng trong tư vấn:** chatbot tự lấy mọi sản phẩm đang bán và còn hàng, chưa có cờ để admin loại riêng sản phẩm khỏi tư vấn.

**Chưa test tự động được / ngoài phạm vi lần này:**

- Chất lượng câu trả lời của mô hình AI thật (đã mock theo yêu cầu).
- Tìm kiếm vector pgvector thật: `PgVectorSearchIntegrationTest` cần một Postgres riêng có pgvector, đã bị loại khỏi `mvnw test` mặc định.
- Thanh toán MoMo thật (đã mock gateway, có test chữ ký và số tiền IPN).
- Đăng nhập Google/Facebook thật (đã có test riêng với verifier giả từ trước).
- Frontend: chưa viết test cho checkout, trang chi tiết sản phẩm, biểu đồ kỹ năng, chatbot và trang admin. Trong phần frontend, lần này chỉ phủ 4 luồng chính được yêu cầu.
- Yêu cầu phi chức năng: thời gian tải dưới 3 giây và responsive trên thiết bị thật.

## 4. Nhật ký giao việc

### Antigravity (agy)

**Lần 1, giai đoạn 2: lập checklist test (chỉ đọc)**
```
agy -p "Bạn đang ở thư mục gốc repo D:\Nien_Luan: website đồ chơi STEM. Backend Spring Boot ở backend/src/main/java/com/brainblocks/backend, frontend React ở frontend/src. Đề bài nằm trong file CLAUDE.md ở gốc repo (mục 2.1-2.14 và mục C). CHỈ ĐỌC: không sửa, tạo hay xóa file nào. Mục tiêu: đối chiếu đề bài với code và lập CHECKLIST CHỨC NĂNG CẦN TEST, chia 4 cụm: (A) Người dùng & Hồ sơ trẻ (...); (B) Sản phẩm & Kỹ năng (...); (C) Giỏ hàng - Đơn hàng - Thanh toán (...); (D) Chatbot AI & RAG (...). Trình bày bằng 4 bảng markdown, mỗi dòng một chức năng, các cột: Chức năng | Đã có code? | Case chính cần test | Case lỗi cần thử. Ngắn gọn, mỗi ô tối đa ~15 từ, không quá 12 dòng mỗi bảng. Chỉ ghi tên class có thật; không chắc thì ghi 'chưa rõ'. Cuối cùng liệt kê tối đa 5 chức năng trong đề bài chưa có code."
```
Kết quả: chạy được (exit 0). Trả 4 bảng với 31 chức năng, kèm 4 chức năng chưa có code. Claude đã kiểm tra 22 tên class được nêu và **tất cả đều có thật**. Checklist này được dùng làm đầu vào cho Codex.

**Lần 2, giai đoạn 4: xác minh độc lập 5 bug (chỉ đọc)**, giao thêm theo yêu cầu "cho Antigravity làm chung"
```
agy -p "$(cat agy-verify.txt)"
```
Prompt yêu cầu đọc code và kết luận ĐÚNG/SAI/CHƯA CHẮC cho từng bug 1–5 ở mục 2.
Kết quả: chạy được (exit 0). Cả 5 bug đều ĐÚNG, có dẫn file:dòng. Antigravity còn phát hiện thêm 5 API phân trang khác cùng lỗi 500, Claude đã kiểm tra lại và thấy đúng. Mức độ đánh giá có khác Claude ở bug 1 và bug 4 (xem mục 2).

### Codex

Do sandbox của Codex không khởi động được Maven Wrapper (`Cannot start maven from wrapper`, lỗi môi trường chứ không phải quota hay đăng nhập), Codex chỉ viết test. **Claude tự chạy test** và gửi lại các test viết sai để Codex sửa.

| Lần | Lệnh | Việc | Kết quả |
|---|---|---|---|
| 1 | `codex exec --sandbox workspace-write "$(cat codex-A.txt)"` | Viết test cụm A trong `fulltest/users/` | 2 file, 41 test. Claude chạy: **41/41 pass**. Đã đọc lại: test dùng JWT thật, kiểm tra 10 endpoint chặn truy cập hồ sơ bé của người khác, chữ ký JWT bị sửa, token cũ bị chặn sau khi khóa tài khoản hoặc đổi mật khẩu. |
| 2 | `codex exec --sandbox workspace-write "$(cat codex-B.txt)"` | Viết test cụm B trong `fulltest/catalog/` | 3 file, 102 test, tự đánh dấu 2 nghi vấn `// BUG:`. Claude chạy: 99 pass / 3 fail, trong đó 2 bug thật và 1 test sai. |
| 3 | `codex exec --sandbox workspace-write "$(cat codex-C.txt)"` | Viết test cụm C trong `fulltest/orders/` | 2 file, 90 test. Claude chạy: **90/90 pass**. Có test 2 đơn tranh nhau món hàng cuối, IPN chữ ký sai hoặc lệch tiền, quyền sở hữu đơn/giỏ/khiếu nại. |
| 4 | `codex exec --sandbox workspace-write "$(cat codex-D.txt)"` | Viết test cụm D trong `fulltest/chatbot/` (AI mock) | 1 file, 37 test, mock `GeminiService` bằng `@MockitoBean`. Claude chạy: 36 pass / 1 fail, và test fail là bug thật số 6. Test có kiểm tra API key không lọt ra response, tài liệu DISABLED không vào prompt, prompt chứa đúng điểm kỹ năng của bé, AI lỗi không trả 500, và giới hạn hỏi dồn dập. |
| 5 | `codex exec --sandbox workspace-write "$(cat codex-B-fix.txt)"` | Sửa test sai `duplicateSkillNameIsRejected` | Chỉ sửa đúng 1 test, xóa `// BUG:`. Claude chạy lại: pass. |

Không có lệnh nào bị lỗi do hết quota, chưa đăng nhập hay không ra output. Codex tuân thủ đúng thư mục được giao. Claude đã kiểm tra `git status`: ngoài các file test, báo cáo và `package.json`/`package-lock.json` của frontend thì không có file nào khác thay đổi.

Prompt đầy đủ của từng lần giao nằm trong scratchpad của phiên làm việc. Mỗi prompt ghi rõ thư mục được ghi, file cấm đụng, bắt buộc dùng `@ActiveProfiles("test")`, cấm gọi dịch vụ ngoài, và cấm nới assert để test pass khi gặp bug thật.

### Trước đó (thử quy trình, chỉ đọc)

- `agy -p "...tóm tắt kiến trúc backend TỐI ĐA 15 dòng..."`: đúng, có bỏ sót bước kiểm tra `isEnabled()` trong `JwtAuthenticationFilter`.
- `codex exec "...review cụm Người dùng & Hồ sơ trẻ, tối đa 5 bug..."`: tìm ra bug 4 và 5 ở mục 2, Claude đã kiểm tra là đúng.

## 5. File được tạo hoặc sửa

- Mới: `backend/src/test/java/com/brainblocks/backend/fulltest/{users,catalog,orders,chatbot}/*` (Codex viết).
- Mới: `frontend/tests/*`, `frontend/vitest.config.ts` (Claude viết). Test để ngoài `src/` nên `tsc -b` của bước build không kiểm tra chúng, và `npm run build` vẫn chạy được.
- Sửa: `frontend/package.json` (thêm script `test` và devDependencies `vitest`, `@testing-library/*`, `jsdom`), kéo theo `frontend/package-lock.json`.
- Không sửa file nào trong `backend/src/main`, `frontend/src`, `pom.xml` hay `application*.properties`.

## 6. Đợt sửa lỗi (sau báo cáo ban đầu)

Theo yêu cầu "sửa hết test fail", lần này **được sửa code chính**. Không test nào bị nới assert, và không đụng file test trong `fulltest/` hay `frontend/tests/`. Ba bên làm song song, mỗi bên một nhóm file riêng, sau đó Claude review diff và chạy lại toàn bộ test.

| Ai | Lệnh | Sửa gì | File | Claude review |
|---|---|---|---|---|
| Codex | `codex exec --sandbox workspace-write "$(cat codex-fix.txt)"` | Bug 6: chat trả id/createdAt null. Bug 3: lọc tuổi đảo ngược. Bug 2: `page` lớn gây 500. Test cũ `ProductEmbeddingServiceTest` | `ChatService.java`, `ProductService.java`, `AdminOrderService.java`, `AdminUserService.java`, `ReviewService.java`, `NotificationService.java`, **mới** `util/Pages.java`, `ProductEmbeddingServiceTest.java` | Đạt. `Pages.of` giữ nguyên kiểm tra `page < 0` / `size < 1` (trả 400) và kẹp `page` để offset không tràn int. `ChatService` dựng response từ tin nhắn đã lưu theo vị trí trong danh sách `@OrderBy` (đúng vì tin nhắn mới nối vào cuối). Đánh đổi: mỗi lần trả lời nạp toàn bộ tin nhắn của phiên, chấp nhận được với kích thước phiên chat hiện tại. |
| Antigravity | `agy -p "$(cat agy-fix.txt)" --mode=accept-edits` | Bug 1: ngày sinh tính theo UTC | `ChildFormModal.tsx` | Đạt. Tính ngày theo giờ địa phương, `max` của ô ngày = hôm qua, khớp với `v < today()` và `@Past`. Antigravity tự chạy test: 9/9 pass. |
| Claude | (tự làm) | Bug 7: đăng xuất / đổi tài khoản không đồng bộ giữa các tab | `tokenStorage.ts` (thêm `reload()`), `AuthContext.tsx` (nghe sự kiện `storage`, xóa cache react-query khi phiên đổi) | `tsc -b` sạch. |

**Kết quả chạy lại toàn bộ:**

| Phần | Test | Pass | Fail |
|---|---:|---:|---:|
| Backend (`./mvnw test`, H2) | 469 | **469** | 0 |
| Frontend (`npm test`) | 53 | **53** | 0 |
| **Tổng** | **522** | **522** | **0** |

`npm run build` của frontend vẫn chạy được.

**Chưa sửa** (không có test nào fail, ngoài phạm vi đợt này):
- Bug 4: ghi đè khi cập nhật đồng thời tài khoản (cần thêm `@Version` vào `User` hoặc khóa dòng như `changeMyPassword`).
- Bug 5: `@Past` chặn bé sinh hôm nay. Nếu đổi sang `@PastOrPresent` thì phải sửa cả form frontend (đang khớp với `@Past`).
- Phía backend của bug 7: chưa có API đăng xuất thu hồi token. Frontend đã đồng bộ giữa các tab, nhưng token bị sao chép ra ngoài vẫn dùng được tới khi hết hạn.
- Message lỗi mới `ageFrom must not be greater than ageTo` chưa có bản dịch trong `frontend/src/api/client.ts`. Giao diện không tạo ra trường hợp này nên không ảnh hưởng người dùng.

## 7. Đợt sửa lỗi thứ hai: các mục "chưa sửa" ở mục 6

Ba mục còn lại ở mục 6 **đã sửa xong**, cụ thể:

| Ai | Lệnh | Việc | File | Claude kiểm tra |
|---|---|---|---|---|
| Codex | `codex exec --sandbox workspace-write "$(cat codex-fix2.txt)"` | Bug 4: ghi đè khi cập nhật đồng thời. Bug 5: `@Past` thành `@PastOrPresent` | `UserService.java`, `AdminUserService.java` (khóa dòng bằng `lockId` trước khi đọc, giống `changeMyPassword`; không đổi schema), `ChildProfileRequest.java`. `PasswordResetService` đã khóa sẵn nên giữ nguyên. Test mới `fulltest/users/UserUpdateFixesTest.java` (6 test, có test 2 luồng đồng thời thật). | **Đã kiểm chứng test có tác dụng:** tạm bỏ 2 dòng `lockId` thì 3/6 test fail, trong đó `concurrentProfileUpdateCannotUndoAdminLock` tái hiện đúng việc tài khoản bị mở khóa lại. Khôi phục code thì 6/6 pass. |
| Antigravity | `agy -p "$(cat agy-fix2.txt)" --mode=accept-edits` | Form cho phép chọn ngày hôm nay, khớp `@PastOrPresent` | `ChildFormModal.tsx` (`v <= today()`, `max={today()}`, bỏ `yesterday()`) | Đạt. Thêm test frontend "bé sinh hôm nay được chấp nhận". |
| Codex | `codex exec --sandbox workspace-write "$(cat codex-logout.txt)"` | Phía backend của bug 7: **API đăng xuất chỉ thu hồi token của thiết bị hiện tại** (người dùng chọn phương án này thay vì đăng xuất mọi thiết bị) | `JwtService` (mỗi token có `jti` UUID), `JwtAuthenticationFilter` (từ chối `jti` đã thu hồi), `AuthController` (`POST /api/auth/logout`, luôn trả 200). Mới: `entity/RevokedToken`, `repository/RevokedTokenRepository`, `service/auth/LogoutService` (có job dọn bản ghi hết hạn mỗi giờ), test `fulltest/users/LogoutApiTest.java` (7 test) | Đạt. Token cũ chưa có `jti`, phát trước khi triển khai, vẫn dùng được tới khi hết hạn. 2 request logout cùng một token chạy đồng thời có thể bị trả 409, vô hại vì frontend bỏ qua lỗi của lệnh logout. |
| Claude | (tự làm) | Phía frontend: gọi API đăng xuất | `endpoints.ts` (`authApi.logout`, gắn token trực tiếp vào request vì interceptor chạy bất đồng bộ), `AuthContext.tsx` (gọi trước khi xóa phiên, lỗi mạng vẫn đăng xuất ở client). Test mới `frontend/tests/logout.test.tsx` (2 test) | `tsc -b` sạch. |

**Kết quả chạy lại toàn bộ:**

| Phần | Test | Pass | Fail |
|---|---:|---:|---:|
| Backend (`./mvnw test`, H2) | 482 | **482** | 0 |
| Frontend (`npm test`) | 56 | **56** | 0 |
| **Tổng** | **538** | **538** | **0** |

**Lưu ý khi chạy với DB thật:** `spring.jpa.hibernate.ddl-auto=update` sẽ tự tạo bảng mới `revoked_tokens` (2 cột) ở lần khởi động tới. Không bảng cũ nào bị đổi.

**Còn lại (nhỏ):** message lỗi `ageFrom must not be greater than ageTo` chưa có bản dịch tiếng Việt trong `frontend/src/api/client.ts`. Giao diện không tạo ra trường hợp này.

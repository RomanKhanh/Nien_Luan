# Đăng nhập Facebook

## Cấu hình

Tạo ứng dụng tại https://developers.facebook.com/apps/ và thiết lập Facebook Login cho website.
Bật đăng nhập bằng JavaScript SDK, khai báo domain/origin của frontend trong phần cấu hình được phép dùng SDK.
Dùng HTTPS khi triển khai; nếu Meta từ chối localhost HTTP, chạy frontend qua địa chỉ HTTPS và cấu hình đúng domain đó.
Ở chế độ Development, dùng tài khoản có vai trò được phép trong Meta App để thử nghiệm. Trước khi mở công khai,
hoàn tất các yêu cầu quyền truy cập/chế độ Live, URL chính sách riêng tư và hướng dẫn xóa dữ liệu trong dashboard Meta.

Backend `.env`:

```dotenv
FACEBOOK_APP_ID=<App ID>
FACEBOOK_APP_SECRET=<App Secret>
FACEBOOK_API_VERSION=v24.0
```

Frontend `.env`:

```dotenv
VITE_FACEBOOK_APP_ID=<cùng App ID của backend>
VITE_FACEBOOK_API_VERSION=v24.0
```

App Secret chỉ nằm ở backend, không đặt vào biến `VITE_`, không commit `.env`.
Khởi động lại backend và Vite sau khi sửa cấu hình. Chưa có App ID thì nút Facebook ẩn;
backend chưa có đủ cấu hình sẽ trả 503, các cách đăng nhập khác vẫn hoạt động.
Phiên bản API cấu hình ở hai phía phải khớp và còn được Meta hỗ trợ.

## Luồng và giới hạn

### Thử đăng nhập ở local bằng HTTPS

Trong `frontend`, chạy `npm run dev` (hoặc `npm run dev:https`), rồi mở `https://localhost:5173/login`.
Chế độ này dùng chứng chỉ tự ký cho phát triển; trình duyệt có thể hiện cảnh báo chứng chỉ.
Chỉ chấp nhận chứng chỉ này cho địa chỉ localhost do chính bạn chạy. Không dùng chứng chỉ này khi triển khai thật.
Chế độ HTTP tùy chọn `npm run dev:http` cũng dùng cổng 5173; dừng server hiện tại trước khi đổi chế độ.
Trong backend `.env`, đặt `CORS_ORIGINS=http://localhost:5173,https://localhost:5173`
và khởi động lại backend. Proxy Vite vẫn chuyển header Origin của trình duyệt;
nếu thiếu origin HTTPS, Spring trả `403 Invalid CORS request` trước khi xử lý đăng nhập.
Vite báo lỗi nếu cổng đang bận, không tự chuyển sang cổng khác. API và ảnh được proxy tới backend như trước.
Trong Meta App, bật Login with JavaScript SDK và khai báo host/domain frontend được phép tương ứng;
đối chiếu Site URL/origin với `https://localhost:5173/`. Thử bằng tài khoản có vai trò trong app khi ở Development.
Nếu dùng `VITE_API_URL` trỏ thẳng tới backend HTTP, bỏ override đó khi thử HTTPS local để sử dụng proxy `/api`.

Nút từ chối chạy SDK login trên HTTP, có thông báo chờ và nút hủy; sau 60 giây không có callback sẽ cho thử lại.
Request xác minh ở backend có timeout 25 giây. Callback từ lần đã hủy/hết hạn sẽ bị bỏ qua.

- SDK lấy access token sau khi người dùng đồng ý `public_profile,email`.
- `POST /api/auth/facebook` nhận `{ "accessToken": "..." }`, kiểm tra bằng `debug_token`:
  token hợp lệ, loại USER, đúng App ID, đúng hạn, có user id. Sau đó gọi `/me` kèm appsecret_proof,
  kiểm tra user id khớp; không nhận email/user id từ client.
- Định danh bằng Facebook app-scoped id. Tài khoản mới tạo dưới vai trò CUSTOMER, có giỏ hàng và voucher
  giống đăng ký thường; mật khẩu ngẫu nhiên được băm. Có thể đặt mật khẩu bằng Quên mật khẩu.
- Tài khoản đã liên kết tiếp tục đăng nhập theo id, kể cả Facebook không trả email ở lần sau.
- Tài khoản bị khóa bị từ chối. Khi Facebook không trả email hoặc email đã đăng ký,
  giao diện mở bước nhập email và xác minh OTP gửi qua SMTP của BrainBlocks.
- `POST /api/auth/facebook/email/send-code` nhận accessToken và email; backend xác minh Facebook trước khi gửi mã.
  `POST /api/auth/facebook/email/complete` nhận thêm code; xác minh lại Facebook và OTP trước khi phát JWT.
  Mã gắn với cả Facebook id lẫn email, dùng một lần, hết hạn sau 5 phút, tối đa 5 lần nhập sai.
  Giãn cách gửi 60 giây theo cả Facebook id và email. Mã băm lưu trong bộ nhớ: restart backend cần gửi lại mã;
  triển khai nhiều instance cần chuyển kho mã và giới hạn gửi sang kho dùng chung.
- Chỉ liên kết tài khoản CUSTOMER hiện có sau khi người dùng xác minh hộp thư và đồng ý qua giao diện.
  Không liên kết tài khoản ADMIN, không thay thế liên kết Facebook khác. Email mới tạo CUSTOMER như đăng ký thường.
  Cần cấu hình SMTP hoạt động; không bật `app.mail.log-only` khi dùng thật.
- Không lưu access token hoặc App Secret vào database/log. JWT nội bộ được xử lý như các cách đăng nhập khác.

`ddl-auto=update` tự thêm `users.facebook_subject` có ràng buộc duy nhất. Với schema quản lý thủ công,
chạy `docs/sql/facebook-login-migration.sql` trước khi khởi động.

## Kiểm tra thủ công với Meta App thật

Khi Facebook không trả email, backend đọc thêm `/me/permissions` (timeout 3 giây), dùng `debug_token.scopes`
làm dự phòng. Giao diện phân biệt `FB_EMAIL_NOT_GRANTED`, `FB_EMAIL_DECLINED`, `FB_EMAIL_EXPIRED`,
`FB_EMAIL_NOT_RETURNED` và `FB_EMAIL_UNKNOWN`. NOT_GRANTED chỉ khẳng định phiên chưa có quyền,
không tự kết luận quyền chưa được thêm trong dashboard. Không ghi email, user id, token hay phản hồi Meta vào log.
Tài khoản đã liên kết vẫn đăng nhập được nếu thiếu email hoặc API chẩn đoán không khả dụng.

1. Đăng nhập với Facebook có email chưa đăng ký: nhận phiên CUSTOMER, giỏ hàng và voucher.
2. Đăng xuất và đăng nhập lại: cùng tài khoản, không tạo thêm voucher.
3. Hủy popup; từ chối email; thử khi SDK bị chặn; giao diện phải cho phép thử lại/cách đăng nhập khác.
4. Khi thiếu email hoặc email đã có tài khoản: thấy form OTP; nhập đúng mã thì đăng nhập cùng tài khoản CUSTOMER.
5. Thử mã sai/hết hạn/dùng lại, tài khoản bị khóa, email ADMIN, token hết hạn và token App ID khác: không phát JWT.

Tham khảo chính thức (dashboard Meta là nơi đối chiếu cấu hình hiện hành):
- https://developers.facebook.com/docs/facebook-login/web/
- https://developers.facebook.com/docs/graph-api/reference/debug_token/
- https://developers.facebook.com/docs/graph-api/securing-requests/

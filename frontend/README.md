# BrainBlocks – Frontend

React 19 + TypeScript + Vite, Tailwind CSS v4 (token lấy từ design system trong mockup), TanStack Query, React Router, React Hook Form + Zod, Recharts.

## Chạy khi phát triển

```bash
# 1. backend (thư mục backend/, cổng 8080)
./mvnw spring-boot:run

# 2. frontend
npm install
npm run dev        # http://localhost:5173
```

Vite proxy `/api` sang `http://localhost:8080` (đổi bằng biến môi trường `VITE_BACKEND_URL`), nên không cần cấu hình CORS khi dev.
Muốn có sẵn danh mục + sản phẩm mẫu để thử giao diện: đặt `SEED_DEMO_DATA=true` trong `backend/.env` (chỉ tạo khi DB chưa có sản phẩm nào).

## Build

```bash
npm run build      # kiểm tra kiểu (tsc -b) rồi build ra dist/
npm run lint       # oxlint
```

Khi frontend và backend khác domain, đặt `VITE_API_URL` (vd `https://api.example.com/api`) lúc build và thêm domain frontend vào `CORS_ORIGINS` của backend.

## Cấu trúc

```
src/
  api/          client axios (gắn JWT, bóc ApiResponse, dịch lỗi), types khớp DTO backend, endpoints
  auth/         AuthContext (phiên đăng nhập), RequireAuth (chặn route theo vai trò)
  components/   ui/ (Button, Field, Modal, Toast, Badges…), layout/, product/, admin/
  features/     logic dùng lại theo nghiệp vụ: cart, catalog, children, skills, orders, reviews, complaints
  lib/          format tiền / ngày, màu 4 nhóm kỹ năng, nhãn trạng thái
  pages/        mỗi route một file; trang admin nằm trong pages/admin (tải lười khi vào /admin)
```

## Ghi chú

- Điểm kỹ năng hiển thị thang 0–10 = `score / totalProducts` (mức tác động trung bình mỗi món); tỉ lệ phân bổ dùng `percentage` từ backend.
- Thanh toán hiện chỉ COD (backend chưa tích hợp VNPay / MoMo); hai lựa chọn này hiện dạng "Sắp ra mắt".
- Chatbot / RAG chưa có API nên chưa có màn hình tương ứng.

-- =====================================================================
-- Bổ sung các ràng buộc mà spring.jpa.hibernate.ddl-auto=update KHÔNG tự thêm
-- vào bảng đã tồn tại (căn chỉnh entity theo sơ đồ lớp v5).
--
-- Chạy SAU khi đã khởi động app ít nhất một lần với code mới (để Hibernate tạo cột/bảng mới).
-- Script chạy lại nhiều lần vẫn an toàn (idempotent).
-- DB tạo mới từ đầu thì Hibernate đã tự sinh CHECK cho enum, phần 2 chỉ là thêm lần nữa với cùng tên.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Email không phân biệt hoa thường
-- ---------------------------------------------------------------------
-- 1a. KIỂM TRA TRƯỚC: nếu câu này trả về dòng nào thì phải xử lý tay (gộp/xóa bớt tài khoản)
--     rồi mới chạy tiếp, nếu không 1b/1c sẽ lỗi trùng.
SELECT lower(trim(email)) AS normalized_email, count(*) AS total
FROM users
GROUP BY 1
HAVING count(*) > 1;

-- 1b. Chuẩn hóa dữ liệu cũ về chữ thường (code mới luôn lưu và đăng nhập bằng chữ thường)
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));

-- 1c. Chặn trùng ở tầng DB, kể cả khi sau này có đường tạo user khác quên chuẩn hóa
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (lower(email));

-- ---------------------------------------------------------------------
-- 2. CHECK cho các cột vừa đổi từ String sang enum
--    (giống cách Hibernate tự sinh cho orders.status, orders.payment_method)
-- ---------------------------------------------------------------------
ALTER TABLE complaints DROP CONSTRAINT IF EXISTS complaints_type_check;
ALTER TABLE complaints ADD CONSTRAINT complaints_type_check
    CHECK (type IN ('RETURN', 'EXCHANGE', 'CANCEL', 'QUALITY', 'OTHER'));

ALTER TABLE complaints DROP CONSTRAINT IF EXISTS complaints_status_check;
ALTER TABLE complaints ADD CONSTRAINT complaints_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED'));

ALTER TABLE child_products DROP CONSTRAINT IF EXISTS child_products_source_check;
ALTER TABLE child_products ADD CONSTRAINT child_products_source_check
    CHECK (source IN ('PURCHASED', 'MANUAL'));

-- ---------------------------------------------------------------------
-- 3. Chỉ một cấu hình chatbot active tại một thời điểm
--    (partial unique index: chỉ áp dụng cho các dòng active = true)
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_chatbot_configs_active ON chatbot_configs (active) WHERE active;

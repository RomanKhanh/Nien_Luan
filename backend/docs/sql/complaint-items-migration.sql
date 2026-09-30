-- =====================================================================
-- Khiếu nại chuyển từ "một sản phẩm" (complaints.order_item_id) sang "nhiều dòng hàng kèm số lượng"
-- (bảng complaint_items). spring.jpa.hibernate.ddl-auto=update tạo bảng mới nhưng KHÔNG xóa cột cũ.
--
-- Chạy SAU khi đã khởi động app ít nhất một lần với code mới (để Hibernate tạo bảng complaint_items).
-- Script chạy lại nhiều lần vẫn an toàn (idempotent). DB tạo mới từ đầu thì không cần chạy.
-- =====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'complaints' AND column_name = 'order_item_id') THEN
        -- khiếu nại cũ không có số lượng lỗi: lấy cả số lượng đã mua của dòng hàng đó
        INSERT INTO complaint_items (complaint_id, order_item_id, quantity)
        SELECT c.id, c.order_item_id, oi.quantity
        FROM complaints c
        JOIN order_items oi ON oi.id = c.order_item_id
        WHERE NOT EXISTS (SELECT 1 FROM complaint_items ci
                          WHERE ci.complaint_id = c.id AND ci.order_item_id = c.order_item_id);

        ALTER TABLE complaints DROP COLUMN order_item_id;
    END IF;
END $$;

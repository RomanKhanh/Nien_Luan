-- Thêm loại thông báo LOW_STOCK vào CHECK constraint của notifications.type
-- (ddl-auto=update không tự sửa constraint trên bảng đã tồn tại). Chạy lại nhiều lần vẫn an toàn.
-- DB tạo mới từ đầu thì Hibernate đã sinh đủ, không cần chạy.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('ORDER_STATUS', 'SKILL_PROFILE_UPDATED', 'COMPLAINT_UPDATED', 'NEW_ORDER',
                    'ORDER_CANCELLED_BY_CUSTOMER', 'NEW_COMPLAINT', 'NEW_REVIEW', 'LOW_STOCK'));
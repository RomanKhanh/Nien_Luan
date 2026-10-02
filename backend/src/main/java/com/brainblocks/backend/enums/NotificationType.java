package com.brainblocks.backend.enums;

public enum NotificationType {
    // gửi khách hàng
    ORDER_STATUS,
    SKILL_PROFILE_UPDATED,
    COMPLAINT_UPDATED,
    // gửi admin, mỗi loại hiện thành số đếm ở mục tương ứng trên trang quản trị
    NEW_ORDER,
    ORDER_CANCELLED_BY_CUSTOMER,
    NEW_COMPLAINT,
    NEW_REVIEW
}

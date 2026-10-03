package com.brainblocks.backend.dto.response.user;

import com.brainblocks.backend.dto.response.location.AddressResponse;

import java.time.LocalDateTime;

// không có field password - không bao giờ trả mật khẩu ra ngoài
public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled,
        // địa chỉ mặc định để hiển thị: địa chỉ 3 cấp nếu có, không thì chuỗi địa chỉ cũ của tài khoản
        String defaultAddress,
        // địa chỉ mặc định 3 cấp để tự điền ở trang thanh toán; null nếu chưa chọn
        AddressResponse defaultShippingAddress,
        LocalDateTime createdAt
) {
}

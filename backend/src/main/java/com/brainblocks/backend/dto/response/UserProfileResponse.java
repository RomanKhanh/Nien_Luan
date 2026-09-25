package com.brainblocks.backend.dto.response;

import java.time.LocalDateTime;

// không có field password - không bao giờ trả mật khẩu ra ngoài
public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled,
        String defaultAddress,
        LocalDateTime createdAt
) {
}

package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// token lấy từ link trong email; mật khẩu mới cùng quy tắc với lúc đăng ký
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}

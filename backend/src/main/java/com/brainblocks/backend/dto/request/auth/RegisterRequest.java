package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        // mã 6 số gửi tới email qua POST /api/auth/register/send-code (EmailVerificationService)
        String verificationCode
) {
}
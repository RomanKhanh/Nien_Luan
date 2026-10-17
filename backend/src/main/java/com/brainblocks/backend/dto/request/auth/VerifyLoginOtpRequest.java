package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// code: mã 6 số trong email đăng nhập (LoginOtpService)
public record VerifyLoginOtpRequest(@NotBlank @Email String email, @NotBlank String code) {
}

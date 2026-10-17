package com.brainblocks.backend.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendLoginOtpRequest(@NotBlank @Email String email) {
}

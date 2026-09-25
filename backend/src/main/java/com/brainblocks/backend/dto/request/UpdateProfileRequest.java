package com.brainblocks.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        // cho phép để trống; nếu nhập thì chỉ gồm số, có thể bắt đầu bằng +
        @Pattern(regexp = "^$|^\\+?[0-9]{9,14}$", message = "must be a valid phone number") String phone,
        @Size(max = 255) String defaultAddress
) {
}

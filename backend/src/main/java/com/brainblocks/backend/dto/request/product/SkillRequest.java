package com.brainblocks.backend.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkillRequest(
        // mã cố định để code và chatbot tham chiếu (vd LOGIC); chỉ gồm chữ in hoa và dấu gạch dưới
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z_]{1,29}$", message = "must be 2-30 uppercase letters or underscores")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description
) {
}

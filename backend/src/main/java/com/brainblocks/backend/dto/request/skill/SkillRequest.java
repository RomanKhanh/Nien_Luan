package com.brainblocks.backend.dto.request.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SkillRequest(
        // định danh ổn định như LOGIC, PROBLEM_SOLVING; service chuẩn hóa về chữ hoa và kiểm tra trùng
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "must contain only letters, digits and underscore")
        String code,
        @NotBlank @Size(max = 100) String name,
        String description
) {
}

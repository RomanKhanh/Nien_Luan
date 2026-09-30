package com.brainblocks.backend.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// PUT thay toàn bộ: danh sách ảnh và chỉ số kỹ năng gửi lên là trạng thái mới của sản phẩm
public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) String description,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Min(0) @Max(1_000_000) int stockQuantity,
        @Min(0) @Max(18) int minAge,
        @Min(0) @Max(18) int maxAge,
        @NotNull @Positive Long categoryId,
        // ảnh đầu tiên là ảnh đại diện; bỏ trống = sản phẩm chưa có ảnh
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> imageUrls,
        List<@NotNull @Valid SkillImpactRequest> skillImpacts,
        // null khi tạo mới = đang bán; khi sửa = giữ nguyên
        Boolean active
) {
}

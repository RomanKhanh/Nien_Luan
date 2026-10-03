package com.brainblocks.backend.dto.request.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

// PUT thay toàn bộ: chỉ số kỹ năng gửi lên là trạng thái mới của sản phẩm.
// Ảnh không nằm ở đây: tải lên / xóa / sắp xếp qua AdminProductImageController
public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) String description,
        // link YouTube (watch, youtu.be, shorts, embed); rỗng = không có video
        @Size(max = 500)
        @Pattern(regexp = "^\\s*$|^\\s*(https?://)?(www\\.|m\\.)?(youtube\\.com/(watch\\?(.*&)?v=|shorts/|embed/|live/)|youtu\\.be/)[A-Za-z0-9_-]{11}([?&#/].*)?\\s*$",
                message = "must be a YouTube video link")
        String videoUrl,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Min(0) @Max(1_000_000) int stockQuantity,
        @Min(0) @Max(18) int minAge,
        @Min(0) @Max(18) int maxAge,
        @NotNull @Positive Long categoryId,
        // thông số vận chuyển của gói hàng sau đóng gói: gram và cm nguyên, bắt buộc
        @NotNull(message = "Vui lòng nhập cân nặng sau đóng gói")
        @Positive(message = "Cân nặng phải lớn hơn 0")
        @Max(value = 50_000, message = "Cân nặng tối đa 50.000 g (50 kg)")
        Integer weightGrams,
        @NotNull(message = "Vui lòng nhập chiều dài gói hàng")
        @Positive(message = "Chiều dài phải lớn hơn 0")
        @Max(value = 200, message = "Chiều dài tối đa 200 cm")
        Integer lengthCm,
        @NotNull(message = "Vui lòng nhập chiều rộng gói hàng")
        @Positive(message = "Chiều rộng phải lớn hơn 0")
        @Max(value = 200, message = "Chiều rộng tối đa 200 cm")
        Integer widthCm,
        @NotNull(message = "Vui lòng nhập chiều cao gói hàng")
        @Positive(message = "Chiều cao phải lớn hơn 0")
        @Max(value = 200, message = "Chiều cao tối đa 200 cm")
        Integer heightCm,
        List<@NotNull @Valid SkillImpactRequest> skillImpacts,
        // null khi tạo mới = đang bán; khi sửa = giữ nguyên
        Boolean active
) {
}

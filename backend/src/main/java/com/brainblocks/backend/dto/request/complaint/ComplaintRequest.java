package com.brainblocks.backend.dto.request.complaint;

import com.brainblocks.backend.enums.ComplaintType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ComplaintRequest(
        @NotNull @Positive Long orderId,
        // sản phẩm cụ thể trong đơn; bỏ trống = khiếu nại cả đơn
        @Positive Long orderItemId,
        @NotNull ComplaintType type,
        @NotBlank @Size(max = 2000) String content
) {
}

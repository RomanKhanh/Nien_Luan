package com.brainblocks.backend.dto.request.shipping;

import jakarta.validation.constraints.NotNull;

// báo giá phí vận chuyển cho giỏ hàng hiện tại tới tỉnh/thành này (mã theo GET /api/locations/provinces)
public record ShippingQuoteRequest(
        @NotNull(message = "Vui lòng chọn tỉnh/thành") Integer provinceCode
) {
}

package com.brainblocks.backend.dto.response.order;

import java.math.BigDecimal;

// dữ liệu kèm lỗi 409 khi phí vận chuyển đã đổi: phí mới, tiền hàng và tổng thanh toán mới
public record ShippingFeeChangedResponse(BigDecimal shippingFee, BigDecimal subtotal, BigDecimal totalAmount) {
}

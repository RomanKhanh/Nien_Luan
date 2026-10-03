package com.brainblocks.backend.exception;

import java.math.BigDecimal;

/**
 * Phí vận chuyển server tính lúc đặt hàng khác phí khách đã thấy (giỏ đổi giữa lúc báo giá và lúc đặt).
 * GlobalExceptionHandler trả 409 kèm phí mới để khách xác nhận lại.
 */
public class ShippingFeeChangedException extends RuntimeException {
    private final BigDecimal shippingFee;
    private final BigDecimal subtotal;

    public ShippingFeeChangedException(String message, BigDecimal shippingFee, BigDecimal subtotal) {
        super(message);
        this.shippingFee = shippingFee;
        this.subtotal = subtotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}

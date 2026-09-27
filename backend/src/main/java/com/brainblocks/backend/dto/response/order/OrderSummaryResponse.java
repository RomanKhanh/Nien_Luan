package com.brainblocks.backend.dto.response.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id, String orderCode, String customerName, String customerEmail,
        BigDecimal totalAmount, String status, String paymentMethod, LocalDateTime createdAt
) {
}

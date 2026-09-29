package com.brainblocks.backend.dto.response.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long orderId, String status, BigDecimal amount, String transactionId, LocalDateTime paidAt) {}
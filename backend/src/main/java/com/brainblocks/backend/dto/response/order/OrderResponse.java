package com.brainblocks.backend.dto.response.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, String orderCode, String receiverName, String receiverPhone,
                            String shippingAddress, BigDecimal totalAmount, String status,
                            String paymentMethod, List<OrderItemResponse> items, LocalDateTime createdAt) {}

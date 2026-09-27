package com.brainblocks.backend.dto.response.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long id, List<CartItemResponse> items, BigDecimal totalAmount) {
}

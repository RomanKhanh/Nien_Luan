package com.brainblocks.backend.dto.response.cart;

import java.math.BigDecimal;

// stockQuantity và active để frontend báo "sắp hết hàng" / "ngừng bán" ngay trong giỏ, trước khi khách đặt hàng
public record CartItemResponse(Long id, Long productId, String productName, String thumbnailUrl,
                               BigDecimal price, int quantity, BigDecimal subtotal,
                               int stockQuantity, boolean active) {}

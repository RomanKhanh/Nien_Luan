package com.brainblocks.backend.dto.response.product;

public record LowStockProductResponse(Long id, String name, String categoryName, int stockQuantity) {
}

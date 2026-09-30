package com.brainblocks.backend.dto.response.child_product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChildProductResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal price,
        String source,
        LocalDateTime addedAt
) {
}

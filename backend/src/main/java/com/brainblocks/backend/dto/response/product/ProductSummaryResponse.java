package com.brainblocks.backend.dto.response.product;

import java.math.BigDecimal;

public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        String thumbnailUrl,
        int minAge,
        int maxAge,
        boolean inStock
) {
}

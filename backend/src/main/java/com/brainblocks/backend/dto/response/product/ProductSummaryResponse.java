package com.brainblocks.backend.dto.response.product;

import java.math.BigDecimal;
import java.util.List;

// một dòng trong danh sách sản phẩm (product card)
public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        int minAge,
        int maxAge,
        int stockQuantity,
        boolean active,
        String thumbnailUrl,
        Long categoryId,
        String categoryName,
        List<ProductSkillImpactResponse> skillImpacts,
        // chỉ tính đánh giá đang hiển thị; chưa có đánh giá thì averageRating = 0
        double averageRating,
        long reviewCount
) {
}

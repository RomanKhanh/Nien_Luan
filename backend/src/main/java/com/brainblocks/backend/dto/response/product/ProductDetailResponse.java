package com.brainblocks.backend.dto.response.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        String videoUrl,
        BigDecimal price,
        int minAge,
        int maxAge,
        int stockQuantity,
        boolean active,
        Long categoryId,
        String categoryName,
        List<ProductImageResponse> images,
        List<ProductSkillImpactResponse> skillImpacts,
        double averageRating,
        long reviewCount,
        // số đánh giá theo từng mức sao 1..5 (mức chưa có đánh giá = 0)
        Map<Integer, Long> ratingBreakdown,
        // tổng số lượng đã bán, không tính đơn đã hủy
        long soldCount,
        LocalDateTime createdAt
) {
}

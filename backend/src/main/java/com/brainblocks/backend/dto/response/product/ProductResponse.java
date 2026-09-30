package com.brainblocks.backend.dto.response.product;

import com.brainblocks.backend.dto.response.review.ReviewSummaryResponse;
import com.brainblocks.backend.dto.response.skill.SkillImpactResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        int minAge,
        int maxAge,
        boolean active,
        Long categoryId,
        String categoryName,
        List<ProductImageResponse> images,
        List<SkillImpactResponse> skillImpacts,
        ReviewSummaryResponse reviewSummary,
        LocalDateTime createdAt
) {
}

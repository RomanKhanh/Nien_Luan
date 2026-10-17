package com.brainblocks.backend.dto.response.chat;

import com.brainblocks.backend.dto.response.product.ProductSkillImpactResponse;
import java.math.BigDecimal;
import java.util.List;

public record ChatSuggestedProductResponse(Long productId, String name, BigDecimal price, int minAge,
        int maxAge, String thumbnailUrl, List<ProductSkillImpactResponse> skillImpacts, String reason) {}

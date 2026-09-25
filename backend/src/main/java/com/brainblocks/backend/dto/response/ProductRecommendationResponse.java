package com.brainblocks.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductRecommendationResponse(
        Long productId,
        String name,
        BigDecimal price,
        int minAge,
        int maxAge,
        String thumbnailUrl,
        int score,
        // lý do đề xuất, hiển thị cho phụ huynh và dùng lại cho chatbot
        List<String> reasons
) {
}

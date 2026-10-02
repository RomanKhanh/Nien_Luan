package com.brainblocks.backend.dto.response.product;

import com.brainblocks.backend.dto.response.skill.SkillGainResponse;

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
        List<String> reasons,
        // điểm từng nhóm kỹ năng của bé tăng bao nhiêu nếu thêm sản phẩm này
        List<SkillGainResponse> skillGains
) {
}

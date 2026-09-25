package com.brainblocks.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

// một mốc trên lộ trình: trạng thái hồ sơ kỹ năng ngay sau khi bé có thêm sản phẩm này
public record SkillTimelinePointResponse(
        Long childProductId,
        Long productId,
        String productName,
        String source,
        LocalDateTime addedAt,
        int totalProducts,
        List<SkillScoreResponse> skillScores
) {
}

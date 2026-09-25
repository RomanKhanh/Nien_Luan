package com.brainblocks.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record SkillProfileResponse(
        Long childProfileId,
        int totalProducts,
        LocalDateTime updatedAt,
        List<SkillScoreResponse> skillScores,
        // nhóm đang được chú trọng nhất / ít được khai thác nhất; null khi chưa đủ dữ liệu để so sánh
        SkillScoreResponse strongestSkill,
        SkillScoreResponse weakestSkill
) {
}

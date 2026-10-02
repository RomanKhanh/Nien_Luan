package com.brainblocks.backend.dto.response.skill;

// điểm một nhóm kỹ năng của bé hiện tại và sau khi thêm một sản phẩm (thang 0-10, lợi ích giảm dần)
public record SkillGainResponse(
        Long skillId,
        String skillCode,
        String skillName,
        double currentScore,
        double projectedScore,
        double gain
) {
}

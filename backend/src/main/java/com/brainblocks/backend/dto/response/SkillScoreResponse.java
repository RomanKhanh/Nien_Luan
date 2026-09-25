package com.brainblocks.backend.dto.response;

public record SkillScoreResponse(
        Long skillId,
        String skillCode,
        String skillName,
        double score,
        double percentage
) {
}

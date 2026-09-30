package com.brainblocks.backend.dto.response.skill;

public record SkillImpactResponse(
        Long skillId,
        String skillCode,
        String skillName,
        int impactIndex
) {
}

package com.brainblocks.backend.dto.response.skill;

public record SkillDetailResponse(
        Long id,
        String code,
        String name,
        String description
) {
}

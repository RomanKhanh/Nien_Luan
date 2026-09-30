package com.brainblocks.backend.dto.request.skill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SkillImpactRequest(
        @NotNull @Positive Long skillId,
        @Min(0) @Max(10) int impactIndex
) {
}

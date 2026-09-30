package com.brainblocks.backend.dto.response.skill;

import java.util.List;

public record SkillTimelineResponse(
        Long childProfileId,
        List<SkillTimelinePointResponse> points
) {
}

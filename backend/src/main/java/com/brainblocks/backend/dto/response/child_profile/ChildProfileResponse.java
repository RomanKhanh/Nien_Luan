package com.brainblocks.backend.dto.response.child_profile;

import com.brainblocks.backend.dto.response.skill.SkillResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ChildProfileResponse(
        Long id,
        String name,
        LocalDate birthDate,
        int age,
        String gender,
        String note,
        List<SkillResponse> interestedSkills,
        LocalDateTime createdAt
) {
}

package com.brainblocks.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record ChildProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Past LocalDate birthDate,
        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "must be MALE, FEMALE or OTHER") String gender,
        @Size(max = 1000) String note,
        // id các nhóm kỹ năng quan tâm; bỏ trống = không chọn nhóm nào
        Set<@NotNull @Positive Long> interestedSkillIds
) {
}

package com.brainblocks.backend.dto.request.product;

import com.brainblocks.backend.dto.request.skill.SkillImpactRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull @Positive BigDecimal price,
        @Min(0) int stockQuantity,
        @Min(0) int minAge,
        @Min(0) int maxAge,
        @NotNull @Positive Long categoryId,
        // chỉ số tác động theo từng nhóm kỹ năng; service kiểm tra trùng skillId
        @NotNull List<@Valid SkillImpactRequest> skillImpacts
) {
    // ràng buộc {minAge <= maxAge} trên sơ đồ lớp
    @JsonIgnore
    @AssertTrue(message = "minAge must be less than or equal to maxAge")
    public boolean isAgeRangeValid() {
        return minAge <= maxAge;
    }
}

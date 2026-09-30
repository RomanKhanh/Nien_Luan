package com.brainblocks.backend.dto.response.product;

// chỉ số tác động của sản phẩm lên một nhóm kỹ năng, thang 0 - 10
public record ProductSkillImpactResponse(Long skillId, String skillCode, String skillName, int impactIndex) {
}

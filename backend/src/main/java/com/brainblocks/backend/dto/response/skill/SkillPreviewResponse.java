package com.brainblocks.backend.dto.response.skill;

import java.util.List;

/**
 * Dự kiến hồ sơ kỹ năng của bé thay đổi thế nào nếu thêm một sản phẩm.
 * gains chỉ gồm các nhóm sản phẩm có tác động; bé đã có sản phẩm thì gains rỗng.
 */
public record SkillPreviewResponse(
        Long childProfileId,
        Long productId,
        boolean alreadyOwned,
        List<SkillGainResponse> gains
) {
}

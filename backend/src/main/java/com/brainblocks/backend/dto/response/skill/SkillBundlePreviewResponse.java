package com.brainblocks.backend.dto.response.skill;

import java.util.List;

/**
 * Dự kiến hồ sơ kỹ năng nếu bé nhận cùng lúc nhiều sản phẩm (vd các món gán cho bé lúc thanh toán).
 * gains gồm mọi nhóm kỹ năng; alreadyOwnedProductIds là các món bé đã có nên không được tính thêm.
 */
public record SkillBundlePreviewResponse(
        Long childProfileId,
        List<Long> alreadyOwnedProductIds,
        List<SkillGainResponse> gains
) {
}

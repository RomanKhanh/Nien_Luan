package com.brainblocks.backend.dto.response.product;

import com.brainblocks.backend.dto.response.skill.SkillGainResponse;

import java.util.List;

/**
 * Một sản phẩm trong danh sách "Hợp với bé": thông tin sản phẩm + điểm từng nhóm kỹ năng của bé tăng bao nhiêu.
 * fitScore = tổng mức tăng vào các nhóm đang lọc (không lọc kỹ năng = mọi nhóm), dùng để sắp "bổ sung nhiều nhất".
 */
public record ProductMatchResponse(
        ProductSummaryResponse product,
        double fitScore,
        List<SkillGainResponse> gains
) {
}

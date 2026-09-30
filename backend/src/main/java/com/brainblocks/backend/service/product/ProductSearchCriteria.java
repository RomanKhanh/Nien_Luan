package com.brainblocks.backend.service.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều kiện lọc danh sách sản phẩm (đề 2.2). Mọi field đều có thể null = không lọc theo field đó.
 *
 * @param ageFrom         cùng ageTo: khoảng tuổi, sản phẩm khớp khi độ tuổi phù hợp giao với khoảng này
 *                        (lọc theo tuổi của một bé: ageFrom = ageTo = tuổi bé)
 * @param skillCodes      nhóm kỹ năng: sản phẩm khớp khi tác động mạnh (>= ProductService.SKILL_FILTER_MIN_IMPACT)
 *                        vào ít nhất một nhóm được chọn
 * @param includeInactive true chỉ dành cho admin: lấy cả sản phẩm đã ẩn
 */
public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        Integer ageFrom,
        Integer ageTo,
        List<String> skillCodes,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        boolean includeInactive
) {
    public boolean hasSkillFilter() {
        return skillCodes != null && !skillCodes.isEmpty();
    }
}

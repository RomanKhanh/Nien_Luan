package com.brainblocks.backend.dto.response.product;

import java.math.BigDecimal;

/**
 * Một gợi ý dưới ô tìm kiếm. label luôn là tên gốc có dấu dù người dùng gõ không dấu.
 * type = PRODUCT (id = mã sản phẩm, kèm ảnh và giá), CATEGORY (id = mã danh mục), SKILL (code = mã nhóm kỹ năng).
 */
public record SearchSuggestionResponse(
        String type,
        String label,
        Long id,
        String code,
        String thumbnailUrl,
        BigDecimal price
) {
    public static SearchSuggestionResponse product(Long id, String name, String thumbnailUrl, BigDecimal price) {
        return new SearchSuggestionResponse("PRODUCT", name, id, null, thumbnailUrl, price);
    }

    public static SearchSuggestionResponse category(Long id, String name) {
        return new SearchSuggestionResponse("CATEGORY", name, id, null, null, null);
    }

    public static SearchSuggestionResponse skill(String code, String name) {
        return new SearchSuggestionResponse("SKILL", name, null, code, null, null);
    }
}

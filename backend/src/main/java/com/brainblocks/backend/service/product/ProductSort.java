package com.brainblocks.backend.service.product;

import java.util.Locale;

// các kiểu sắp xếp danh sách sản phẩm; client gửi tên viết thường (vd price_asc)
public enum ProductSort {
    // mức độ phù hợp: tổng chỉ số tác động vào các nhóm kỹ năng đang lọc (không lọc kỹ năng = mọi nhóm)
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST,
    NAME;

    public static ProductSort from(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sort: " + value);
        }
    }
}

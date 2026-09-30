package com.brainblocks.backend.dto.response.product;

// productCount: số sản phẩm đang bán thuộc danh mục
public record CategoryResponse(Long id, String name, String description, long productCount) {
}

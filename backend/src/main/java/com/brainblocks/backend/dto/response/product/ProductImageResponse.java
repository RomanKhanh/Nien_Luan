package com.brainblocks.backend.dto.response.product;

public record ProductImageResponse(Long id, String url, int displayOrder, boolean thumbnail) {
}

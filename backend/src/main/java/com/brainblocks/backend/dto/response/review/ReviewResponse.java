package com.brainblocks.backend.dto.response.review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long productId,
        String productName,
        String customerName,
        int rating,
        String comment,
        boolean visible,
        LocalDateTime createdAt
) {
}

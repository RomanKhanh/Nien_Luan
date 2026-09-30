package com.brainblocks.backend.dto.response.review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        int rating,
        String comment,
        String customerName,
        LocalDateTime createdAt
) {
}

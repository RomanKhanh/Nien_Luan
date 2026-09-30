package com.brainblocks.backend.dto.response.complaint;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintResponse(
        Long id, Long orderId, String orderCode, String type, String content,
        String status, String response, List<ComplaintItemResponse> items,
        LocalDateTime createdAt, LocalDateTime handledAt
) {}

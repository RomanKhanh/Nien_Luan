package com.brainblocks.backend.dto.response.complaint;

import java.time.LocalDateTime;

public record ComplaintResponse(
        Long id,
        Long orderId,
        String orderCode,
        String orderStatus,
        // null khi khiếu nại cả đơn
        Long orderItemId,
        String productName,
        String customerName,
        String customerEmail,
        String type,
        String content,
        String status,
        String response,
        String handledByName,
        LocalDateTime createdAt,
        LocalDateTime handledAt
) {
}

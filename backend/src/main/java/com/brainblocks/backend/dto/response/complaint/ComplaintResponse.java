package com.brainblocks.backend.dto.response.complaint;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintResponse(
        Long id, Long orderId, String orderCode, String orderStatus,
        String customerName, String customerEmail, String type, String content,
        String status, String response, String handledByName, List<ComplaintItemResponse> items,
        LocalDateTime createdAt, LocalDateTime handledAt
) {}

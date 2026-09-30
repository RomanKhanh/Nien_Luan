package com.brainblocks.backend.dto.response.complaint;

public record ComplaintItemResponse(Long orderItemId, Long productId, String productName, int quantity) {}
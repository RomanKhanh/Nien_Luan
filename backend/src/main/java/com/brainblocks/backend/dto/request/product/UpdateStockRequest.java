package com.brainblocks.backend.dto.request.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateStockRequest(@Min(0) @Max(1_000_000) int stockQuantity) {
}

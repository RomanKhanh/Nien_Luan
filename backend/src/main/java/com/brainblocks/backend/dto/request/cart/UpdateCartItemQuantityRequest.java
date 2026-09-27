package com.brainblocks.backend.dto.request.cart;

import jakarta.validation.constraints.Min;

public record UpdateCartItemQuantityRequest(@Min(1) int quantity) {
}

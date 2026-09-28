package com.brainblocks.backend.dto.request.cart;

import com.brainblocks.backend.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCartItemQuantityRequest(@Min(1) @Max(CartItem.MAX_QUANTITY) int quantity) {
}

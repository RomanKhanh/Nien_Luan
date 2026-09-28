package com.brainblocks.backend.dto.request.cart;

import com.brainblocks.backend.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull @Positive Long productId,
        @Min(1) @Max(CartItem.MAX_QUANTITY) int quantity
) {}

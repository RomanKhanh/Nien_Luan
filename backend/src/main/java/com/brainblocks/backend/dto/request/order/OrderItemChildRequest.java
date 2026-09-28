package com.brainblocks.backend.dto.request.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// sản phẩm productId trong giỏ được mua cho bé childProfileId (quan hệ "dành cho" OrderItem - ChildProfile)
public record OrderItemChildRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Long childProfileId
) {}

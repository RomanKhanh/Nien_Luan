package com.brainblocks.backend.dto.request.order;

import com.brainblocks.backend.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String receiverName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,14}$") String receiverPhone,
        @NotBlank @Size(max = 255) String shippingAddress,
        @NotNull PaymentMethod paymentMethod
) {}

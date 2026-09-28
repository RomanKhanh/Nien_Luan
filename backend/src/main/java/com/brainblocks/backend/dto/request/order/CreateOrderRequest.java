package com.brainblocks.backend.dto.request.order;

import com.brainblocks.backend.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String receiverName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,14}$") String receiverPhone,
        @NotBlank @Size(max = 255) String shippingAddress,
        @NotNull PaymentMethod paymentMethod,
        // chọn bé cho từng sản phẩm trong giỏ ở bước checkout; bỏ trống = không gắn bé nào
        List<@NotNull @Valid OrderItemChildRequest> childAssignments
) {}

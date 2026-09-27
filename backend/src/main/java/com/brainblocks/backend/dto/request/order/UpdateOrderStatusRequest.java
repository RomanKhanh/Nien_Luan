package com.brainblocks.backend.dto.request.order;

import jakarta.validation.constraints.NotNull;
import com.brainblocks.backend.enums.OrderStatus;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}

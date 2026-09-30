package com.brainblocks.backend.dto.request.complaint;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ComplaintItemRequest(
        @NotNull @Positive Long orderItemId,
        @Positive int quantity
) {}
package com.brainblocks.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignProductRequest(@NotNull @Positive Long productId) {
}

package com.brainblocks.backend.dto.request.child_profile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignProductRequest(@NotNull @Positive Long productId) {
}

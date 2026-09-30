package com.brainblocks.backend.dto.request.product;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull Boolean active) {
}

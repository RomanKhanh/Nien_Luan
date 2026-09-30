package com.brainblocks.backend.dto.request.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReorderProductImagesRequest(
        @NotEmpty List<@NotNull @Positive Long> imageIds
) {
}

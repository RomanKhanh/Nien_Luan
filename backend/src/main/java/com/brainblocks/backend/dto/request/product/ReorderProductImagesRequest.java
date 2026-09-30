package com.brainblocks.backend.dto.request.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// đủ và đúng các ảnh hiện có của sản phẩm, theo thứ tự hiển thị mới
public record ReorderProductImagesRequest(
        @NotEmpty List<@NotNull @Positive Long> imageIds
) {
}

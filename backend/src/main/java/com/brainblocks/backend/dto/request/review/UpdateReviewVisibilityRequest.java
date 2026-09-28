package com.brainblocks.backend.dto.request.review;

import jakarta.validation.constraints.NotNull;

public record UpdateReviewVisibilityRequest(@NotNull Boolean visible) {
}

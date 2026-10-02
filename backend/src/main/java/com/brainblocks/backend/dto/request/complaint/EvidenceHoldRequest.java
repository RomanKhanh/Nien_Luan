package com.brainblocks.backend.dto.request.complaint;

import jakarta.validation.constraints.NotNull;

// true = giữ lại bằng chứng, không tự xóa sau thời hạn; false = bỏ giữ
public record EvidenceHoldRequest(@NotNull Boolean hold) {
}

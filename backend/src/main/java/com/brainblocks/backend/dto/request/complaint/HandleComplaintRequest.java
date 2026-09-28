package com.brainblocks.backend.dto.request.complaint;

import com.brainblocks.backend.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleComplaintRequest(
        @NotNull ComplaintStatus status,
        // phản hồi gửi khách; bắt buộc khi kết thúc (RESOLVED / REJECTED), kiểm tra ở service
        @Size(max = 2000) String response
) {
}

package com.brainblocks.backend.dto.request.complaint;

import com.brainblocks.backend.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateComplaintStatusRequest(
        @NotNull ComplaintStatus status,
        @Size(max = 2000) String response
) {}
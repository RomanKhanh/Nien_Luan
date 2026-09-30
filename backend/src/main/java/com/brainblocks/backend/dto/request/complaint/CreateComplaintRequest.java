package com.brainblocks.backend.dto.request.complaint;

import com.brainblocks.backend.enums.ComplaintType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateComplaintRequest(
        @NotNull ComplaintType type,
        @NotBlank @Size(max = 2000) String content,
        // rỗng hoặc không gửi = khiếu nại chung cả đơn, không nhắm sản phẩm cụ thể nào
        @Valid List<ComplaintItemRequest> items
) {}
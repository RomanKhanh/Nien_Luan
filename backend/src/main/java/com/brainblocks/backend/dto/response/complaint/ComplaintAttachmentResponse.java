package com.brainblocks.backend.dto.response.complaint;

import java.time.LocalDateTime;

// thông tin file bằng chứng; nội dung tải qua GET /api/complaints/{complaintId}/attachments/{id}
public record ComplaintAttachmentResponse(
        Long id,
        String kind,
        String contentType,
        String originalName,
        long sizeBytes,
        // lúc file bị xóa tự động; null = vẫn xem được
        LocalDateTime purgedAt
) {
}

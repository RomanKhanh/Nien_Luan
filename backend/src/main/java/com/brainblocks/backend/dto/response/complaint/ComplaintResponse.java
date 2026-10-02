package com.brainblocks.backend.dto.response.complaint;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintResponse(
        Long id, Long orderId, String orderCode, String orderStatus,
        String customerName, String customerEmail, String type, String content,
        String status, String response, String handledByName, List<ComplaintItemResponse> items,
        LocalDateTime createdAt, LocalDateTime handledAt,
        // yêu cầu trả hàng: lúc được tiếp nhận, hạn chót khách gửi hàng về (null nếu không áp dụng),
        // và có bị tự từ chối do khách trễ hạn hay không
        LocalDateTime acceptedAt, LocalDateTime returnDeadline, boolean returnExpired,
        // video mở hàng, ảnh / video tình trạng sản phẩm khách đính kèm
        List<ComplaintAttachmentResponse> attachments,
        // admin giữ lại bằng chứng; ngày file sẽ tự xóa (null nếu chưa đóng, đang giữ lại hoặc đã xóa hết)
        boolean evidenceHold, LocalDateTime evidencePurgeAt
) {}

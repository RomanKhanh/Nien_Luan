package com.brainblocks.backend.dto.response.notification;

import java.util.Map;

// total cho chuông khách hàng; byType cho số đếm từng mục trên menu quản trị (loại không có thì không có key)
public record UnreadCountResponse(
        long total,
        Map<String, Long> byType
) {
}

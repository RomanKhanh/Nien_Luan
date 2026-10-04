package com.brainblocks.backend.dto.response.voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Voucher của khách. type: FREESHIP / PERCENT_OFF / AMOUNT_OFF; value: số % hoặc số tiền (null với FREESHIP);
 * minSubtotal: tiền hàng tối thiểu; maxDiscount: mức giảm tối đa (null = không giới hạn);
 * status: AVAILABLE / USED / EXPIRED; label: tên hiển thị, vd "Giảm 10%".
 */
public record VoucherResponse(Long id, String type, String reason, String label, BigDecimal value,
                              BigDecimal minSubtotal, BigDecimal maxDiscount, String childName,
                              LocalDateTime issuedAt, LocalDateTime expiresAt, String status,
                              LocalDateTime usedAt, Long orderId, String orderCode) {}

package com.brainblocks.backend.dto.response.voucher;

// số voucher đã phát theo trạng thái, cho trang admin
public record VoucherSummaryResponse(long issued, long available, long used, long expired) {}

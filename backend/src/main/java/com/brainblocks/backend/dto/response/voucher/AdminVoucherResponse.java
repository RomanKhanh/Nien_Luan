package com.brainblocks.backend.dto.response.voucher;

// voucher kèm khách sở hữu, cho trang admin
public record AdminVoucherResponse(VoucherResponse voucher, Long customerId, String customerName,
                                   String customerEmail) {}
